package router

import (
	"context"
	"sync"

	core "github.com/exclavenetwork/exclave-core/v5"
	"github.com/exclavenetwork/exclave-core/v5/app/observatory"
	"github.com/exclavenetwork/exclave-core/v5/common"
	"github.com/exclavenetwork/exclave-core/v5/features"
	"github.com/exclavenetwork/exclave-core/v5/features/extension"
)

type LeastPingStrategy struct {
	ctx         context.Context
	observatory extension.Observatory

	config *StrategyLeastPingConfig

	lock       sync.Mutex
	lastChoice string
}

func (l *LeastPingStrategy) GetPrincipleTarget(strings []string) []string {
	return []string{l.PickOutbound(strings)}
}

func (l *LeastPingStrategy) InjectContext(ctx context.Context) {
	l.ctx = ctx
}

func (l *LeastPingStrategy) PickOutbound(strings []string) string {
	if len(strings) == 0 {
		return ""
	}
	l.lock.Lock()
	obs := l.observatory
	if obs == nil {
		obsFeature := core.MustFromContext(l.ctx).GetFeature(extension.ObservatoryType()).(extension.Observatory)
		if l.config != nil && l.config.ObserverTag != "" {
			obs = common.Must2(obsFeature.(features.TaggedFeatures).GetFeaturesByTag(l.config.ObserverTag)).(extension.Observatory)
		} else {
			obs = obsFeature
		}
		l.observatory = obs
	}
	l.lock.Unlock()

	if obs == nil {
		newError("cannot find observatory").WriteToLog()
		return strings[0]
	}

	observeReport, err := obs.GetObservation(l.ctx)
	if err != nil {
		newError("cannot get observe report").Base(err).WriteToLog()
		return strings[0]
	}
	outboundsList := outboundList(strings)
	if result, ok := observeReport.(*observatory.ObservationResult); ok {
		status := result.Status
		probed := make(map[string]bool, len(status))
		leastPing := int64(99999999)
		selectedOutboundName := ""
		for _, v := range status {
			if v == nil {
				continue
			}
			if v.LastTryTime > 0 {
				probed[v.OutboundTag] = true
			}
			if outboundsList.contains(v.OutboundTag) && v.Alive && v.Delay < leastPing {
				selectedOutboundName = v.OutboundTag
				leastPing = v.Delay
			}
		}
		if selectedOutboundName == "" {
			for _, candidate := range strings {
				if !probed[candidate] {
					selectedOutboundName = candidate
					break
				}
			}
		}
		l.lock.Lock()
		if selectedOutboundName == "" && l.config != nil && l.config.StickyChoice {
			selectedOutboundName = l.lastChoice
		}
		l.lastChoice = selectedOutboundName
		l.lock.Unlock()
		return selectedOutboundName
	}

	return ""
}

type outboundList []string

func (o outboundList) contains(name string) bool {
	for _, v := range o {
		if v == name {
			return true
		}
	}
	return false
}

func init() {
	common.Must(common.RegisterConfig((*StrategyLeastPingConfig)(nil), nil))
}

