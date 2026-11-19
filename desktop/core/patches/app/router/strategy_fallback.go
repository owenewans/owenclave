package router

import (
	"context"
	"sync"

	core "github.com/exclavenetwork/exclave-core/v5"
	"github.com/exclavenetwork/exclave-core/v5/app/observatory"
	"github.com/exclavenetwork/exclave-core/v5/common"
	"github.com/exclavenetwork/exclave-core/v5/features"
	"github.com/exclavenetwork/exclave-core/v5/features/extension"
	"github.com/exclavenetwork/exclave-core/v5/features/outbound"
)

type FallbackStrategy struct {
	ctx         context.Context
	observatory extension.Observatory
	ohm         outbound.Manager

	config *StrategyFallbackConfig

	mu             sync.Mutex
	listenerHooked bool
	candidates     []string
	lastPicked     string
}

func (l *FallbackStrategy) GetPrincipleTarget(strings []string) []string {
	return []string{l.PickOutbound(strings)}
}

func (l *FallbackStrategy) InjectContext(ctx context.Context) {
	l.ctx = ctx
}

func (l *FallbackStrategy) ensureInitialized() {
	l.mu.Lock()
	if l.observatory != nil {
		l.mu.Unlock()
		return
	}
	inst := core.MustFromContext(l.ctx)
	if omFeature := inst.GetFeature(outbound.ManagerType()); omFeature != nil {
		l.ohm, _ = omFeature.(outbound.Manager)
	}
	obsFeature := inst.GetFeature(extension.ObservatoryType()).(extension.Observatory)
	if l.config != nil && l.config.ObserverTag != "" {
		l.observatory = common.Must2(obsFeature.(features.TaggedFeatures).GetFeaturesByTag(l.config.ObserverTag)).(extension.Observatory)
	} else {
		l.observatory = obsFeature
	}
	hookTarget, canHook := l.observatory.(*observatory.Observer)
	shouldHook := canHook && !l.listenerHooked
	if shouldHook {
		l.listenerHooked = true
	}
	l.mu.Unlock()

	if shouldHook {
		hookTarget.AddStatusListener(l.onStatusUpdate)
	}
}

// onStatusUpdate immediately evicts active sessions on a lower-priority backup
// outbound as soon as a higher-priority outbound comes back alive.
func (l *FallbackStrategy) onStatusUpdate(updated *observatory.OutboundStatus) {
	if updated == nil || !updated.Alive {
		return
	}
	l.mu.Lock()
	prev := l.lastPicked
	cands := append([]string(nil), l.candidates...)
	ohm := l.ohm
	if prev == "" || prev == updated.OutboundTag || len(cands) == 0 {
		l.mu.Unlock()
		return
	}
	newIdx := indexOfTag(cands, updated.OutboundTag)
	prevIdx := indexOfTag(cands, prev)
	shouldResetPrev := newIdx >= 0 && (prevIdx < 0 || newIdx < prevIdx)
	if shouldResetPrev {
		l.lastPicked = updated.OutboundTag
	}
	l.mu.Unlock()

	if shouldResetPrev {
		resetOutboundConnections(ohm, prev)
	}
}

func (l *FallbackStrategy) PickOutbound(strings []string) string {
	if len(strings) == 0 {
		return ""
	}
	l.ensureInitialized()

	observeReport, err := l.observatory.GetObservation(l.ctx)
	if err != nil {
		newError("cannot get observe report").Base(err).WriteToLog()
		return strings[0]
	}
	result, ok := observeReport.(*observatory.ObservationResult)
	if !ok {
		return strings[0]
	}

	statusByTag := make(map[string]*observatory.OutboundStatus, len(result.Status))
	for _, v := range result.Status {
		if v != nil {
			statusByTag[v.OutboundTag] = v
		}
	}

	chosen := ""
	firstUnprobed := ""
	for _, candidate := range strings {
		st, seen := statusByTag[candidate]
		if !seen || st.LastTryTime == 0 {
			if firstUnprobed == "" {
				firstUnprobed = candidate
			}
			continue
		}
		if st.Alive {
			chosen = candidate
			break
		}
	}

	// Before the initial probe completes on startup, use the first unprobed node
	// instead of returning "" (which would prematurely trigger `fallbackTag`).
	if chosen == "" && firstUnprobed != "" {
		chosen = firstUnprobed
	}

	l.mu.Lock()
	l.candidates = append(l.candidates[:0], strings...)
	prev := l.lastPicked
	ohm := l.ohm
	evictPrev := false
	if chosen != "" && prev != "" && prev != chosen {
		newIdx := indexOfTag(strings, chosen)
		prevIdx := indexOfTag(strings, prev)
		if newIdx >= 0 && prevIdx >= 0 && newIdx < prevIdx {
			evictPrev = true
		}
	}
	l.lastPicked = chosen
	l.mu.Unlock()

	if evictPrev {
		go resetOutboundConnections(ohm, prev)
	}

	return chosen
}

func indexOfTag(list []string, target string) int {
	for i, item := range list {
		if item == target {
			return i
		}
	}
	return -1
}

func init() {
	common.Must(common.RegisterConfig((*StrategyFallbackConfig)(nil), nil))
}

