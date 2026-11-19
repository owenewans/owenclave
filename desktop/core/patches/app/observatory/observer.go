package observatory

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/url"
	"sort"
	"sync"
	"time"

	"github.com/golang/protobuf/proto"

	core "github.com/exclavenetwork/exclave-core/v5"
	"github.com/exclavenetwork/exclave-core/v5/app/persistentstorage"
	"github.com/exclavenetwork/exclave-core/v5/app/persistentstorage/protostorage"
	"github.com/exclavenetwork/exclave-core/v5/common"
	"github.com/exclavenetwork/exclave-core/v5/common/environment"
	"github.com/exclavenetwork/exclave-core/v5/common/environment/envctx"
	v2net "github.com/exclavenetwork/exclave-core/v5/common/net"
	"github.com/exclavenetwork/exclave-core/v5/common/session"
	"github.com/exclavenetwork/exclave-core/v5/common/task"
	"github.com/exclavenetwork/exclave-core/v5/features/extension"
	"github.com/exclavenetwork/exclave-core/v5/features/outbound"
	"github.com/exclavenetwork/exclave-core/v5/transport/internet/tagged"
)

const (
	defaultProbeURL      = "http://cp.cloudflare.com/generate_204"
	bypassFallbackOutTag = "bypass-fallback"
)

type connectionResetter interface {
	ResetConnections()
}

type Observer struct {
	config *Config
	ctx    context.Context

	statusLock      sync.Mutex
	status          []*OutboundStatus
	statusListeners []func(result *OutboundStatus)

	cancel context.CancelFunc

	ohm            outbound.Manager
	persistStorage persistentstorage.ScopedPersistentStorage

	persistOutboundStatusProtoStorage protostorage.ProtoPersistentStorage

	StatusUpdate func(result *OutboundStatus)
}

func (o *Observer) AddStatusListener(fn func(result *OutboundStatus)) {
	if fn == nil {
		return
	}
	o.statusLock.Lock()
	o.statusListeners = append(o.statusListeners, fn)
	o.statusLock.Unlock()
}

func (o *Observer) GetObservation(ctx context.Context) (proto.Message, error) {
	o.statusLock.Lock()
	defer o.statusLock.Unlock()
	copied := make([]*OutboundStatus, len(o.status))
	for i, s := range o.status {
		if s != nil {
			dup := *s
			copied[i] = &dup
		}
	}
	return &ObservationResult{Status: copied}, nil
}

func (o *Observer) Type() interface{} {
	return extension.ObservatoryType()
}

func (o *Observer) Start() error {
	if o.config != nil && len(o.config.SubjectSelector) != 0 {
		if o.config.PersistentProbeResult {
			appEnvironment := envctx.EnvironmentFromContext(o.ctx).(environment.AppEnvironment)
			o.persistStorage = appEnvironment.PersistentStorage()

			outboundStatusStorage, err := o.persistStorage.NarrowScope(o.ctx, []byte("outbound_status"))
			if err != nil {
				return newError("failed to get persistent storage for outbound_status").Base(err)
			}
			o.persistOutboundStatusProtoStorage = outboundStatusStorage.(protostorage.ProtoPersistentStorage)
			list, err := outboundStatusStorage.List(o.ctx, []byte(""))
			if err != nil {
				newError("failed to list persisted outbound status").Base(err).WriteToLog()
			} else {
				for _, v := range list {
					o.loadOutboundStatus(string(v))
				}
			}
		}
		ctx, cancel := context.WithCancel(context.Background())
		o.cancel = cancel
		go o.background(ctx)
	}
	return nil
}

func (o *Observer) Close() error {
	if o.cancel != nil {
		o.cancel()
	}
	return nil
}

func (o *Observer) background(ctx context.Context) {
	sleepTime := time.Second * 10
	if o.config.ProbeInterval != 0 {
		sleepTime = time.Duration(o.config.ProbeInterval)
	}

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}
		hs, ok := o.ohm.(outbound.HandlerSelector)
		if !ok {
			newError("outbound.Manager is not a HandlerSelector").WriteToLog()
			return
		}

		outbounds := hs.Select(o.config.SubjectSelector)

		if len(outbounds) == 0 {
			newError(o.ctx, "no outbound matches subjectSelector ", o.config.SubjectSelector).WriteToLog()
			select {
			case <-ctx.Done():
				return
			case <-time.After(sleepTime):
			}
			continue
		}

		if !o.config.EnableConcurrency {
			sort.Strings(outbounds)
			for _, v := range outbounds {
				result := o.probe(ctx, v)
				o.updateStatusForResult(v, &result)
				select {
				case <-ctx.Done():
					return
				default:
				}
			}
			select {
			case <-ctx.Done():
				return
			case <-time.After(sleepTime):
			}
			continue
		}

		ch := make(chan struct{}, len(outbounds))

		for _, v := range outbounds {
			go func(v string) {
				result := o.probe(ctx, v)
				o.updateStatusForResult(v, &result)
				ch <- struct{}{}
			}(v)
		}

		for range outbounds {
			select {
			case <-ch:
			case <-ctx.Done():
				return
			}
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(sleepTime):
		}
	}
}

func (o *Observer) probe(ctx context.Context, outbound string) ProbeResult {
	errorCollectorForRequest := newErrorCollector()

	httpTransport := http.Transport{
		Proxy: func(*http.Request) (*url.URL, error) {
			return nil, nil
		},
		DialContext: func(ctx context.Context, network string, addr string) (net.Conn, error) {
			var connection net.Conn
			taskErr := task.Run(ctx, func() error {
				dest, err := v2net.ParseDestination(network + ":" + addr)
				if err != nil {
					return newError("cannot understand address").Base(err)
				}
				trackedCtx := session.TrackedConnectionError(o.ctx, errorCollectorForRequest)
				conn, err := tagged.Dialer(trackedCtx, dest, outbound)
				if err != nil {
					return newError("cannot dial remote address ", dest).Base(err)
				}
				connection = conn
				return nil
			})
			if taskErr != nil {
				return nil, newError("cannot finish connection").Base(taskErr)
			}
			return connection, nil
		},
		MaxIdleConns:        2,
		MaxIdleConnsPerHost: 1,
		IdleConnTimeout:     5 * time.Second,
		TLSHandshakeTimeout: 6 * time.Second,
	}
	defer httpTransport.CloseIdleConnections()

	httpClient := &http.Client{
		Transport: &httpTransport,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
		Jar:     nil,
		Timeout: 8 * time.Second,
	}

	var GETTime time.Duration
	err := task.Run(o.ctx, func() error {
		probeURL := defaultProbeURL
		if o.config.ProbeUrl != "" {
			probeURL = o.config.ProbeUrl
		}
		req1, err := http.NewRequestWithContext(ctx, http.MethodGet, probeURL, nil)
		if err != nil {
			return err
		}
		start1 := time.Now()
		resp1, err := httpClient.Do(req1)
		if err != nil {
			return newError("outbound failed to relay connection").Base(err)
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(resp1.Body, 4096))
		_ = resp1.Body.Close()
		rtt1 := time.Since(start1)

		if resp1.StatusCode >= 400 {
			return newError("probe received HTTP status ", resp1.StatusCode)
		}
		GETTime = rtt1

		// UnifiedDelay (Mihomo-compatible 1-RTT measurement): once the proxy tunnel
		// and TLS/TCP session are warm, measure a second request over the established
		// connection so multi-RTT handshake setup does not inflate steady-state ping.
		if !resp1.Close && rtt1 < 4*time.Second {
			req2, reqErr := http.NewRequestWithContext(ctx, http.MethodGet, probeURL, nil)
			if reqErr == nil {
				start2 := time.Now()
				resp2, err2 := httpClient.Do(req2)
				if err2 == nil {
					_, _ = io.Copy(io.Discard, io.LimitReader(resp2.Body, 4096))
					_ = resp2.Body.Close()
					if resp2.StatusCode < 400 {
						GETTime = time.Since(start2)
					}
				}
			}
		}
		if GETTime < time.Millisecond {
			GETTime = time.Millisecond
		}
		return nil
	})
	if err != nil {
		fullerr := newError("underlying connection failed").Base(errorCollectorForRequest.UnderlyingError())
		fullerr = newError("with outbound handler report").Base(fullerr)
		fullerr = newError("GET request failed:", err).Base(fullerr)
		fullerr = newError("the outbound ", outbound, " is dead:").Base(fullerr)
		fullerr = fullerr.AtInfo()
		fullerr.WriteToLog()
		return ProbeResult{Alive: false, LastErrorReason: fullerr.Error()}
	}
	newError("the outbound ", outbound, " is alive:", GETTime.Seconds()).AtInfo().WriteToLog()
	return ProbeResult{Alive: true, Delay: GETTime.Milliseconds()}
}

func (o *Observer) updateStatusForResult(outboundTag string, result *ProbeResult) {
	o.statusLock.Lock()
	var status *OutboundStatus
	wasDead := false
	if location := o.findStatusLocationLockHolderOnly(outboundTag); location != -1 {
		status = o.status[location]
		wasDead = status.LastTryTime > 0 && !status.Alive
	} else {
		status = &OutboundStatus{}
		o.status = append(o.status, status)
	}

	status.LastTryTime = time.Now().Unix()
	status.OutboundTag = outboundTag
	status.Alive = result.Alive
	if result.Alive {
		status.Delay = result.Delay
		status.LastSeenTime = status.LastTryTime
		status.LastErrorReason = ""
	} else {
		status.LastErrorReason = result.LastErrorReason
		status.Delay = 99999999
	}
	if o.config.PersistentProbeResult && o.persistOutboundStatusProtoStorage != nil {
		err := o.persistOutboundStatusProtoStorage.PutProto(o.ctx, outboundTag, status)
		if err != nil {
			newError("failed to persist outbound status").Base(err).WriteToLog()
		}
	}

	snapshot := *status
	cb := o.StatusUpdate
	listeners := make([]func(*OutboundStatus), len(o.statusListeners))
	copy(listeners, o.statusListeners)
	o.statusLock.Unlock()

	if wasDead && result.Alive && o.ohm != nil {
		if h := o.ohm.GetHandler(bypassFallbackOutTag); h != nil {
			if r, ok := h.(connectionResetter); ok {
				r.ResetConnections()
			}
		}
	}

	if cb != nil {
		cb(&snapshot)
	}
	for _, fn := range listeners {
		fn(&snapshot)
	}
}

func (o *Observer) findStatusLocationLockHolderOnly(outbound string) int {
	for i, v := range o.status {
		if v.OutboundTag == outbound {
			return i
		}
	}
	return -1
}

func (o *Observer) loadOutboundStatus(name string) {
	if o.persistOutboundStatusProtoStorage == nil {
		return
	}
	status := &OutboundStatus{}
	err := o.persistOutboundStatusProtoStorage.GetProto(o.ctx, name, status)
	if err != nil {
		newError("failed to load outbound status").Base(err).WriteToLog()
		return
	}
	o.status = append(o.status, status)
}

func New(ctx context.Context, config *Config) (*Observer, error) {
	obs := &Observer{
		config: config,
		ctx:    ctx,
	}

	err := core.RequireFeatures(ctx, func(om outbound.Manager) {
		obs.ohm = om
	})
	if err != nil {
		return nil, newError("Cannot get depended features").Base(err)
	}

	return obs, nil
}

func init() {
	common.Must(common.RegisterConfig((*Config)(nil), func(ctx context.Context, config interface{}) (interface{}, error) {
		return New(ctx, config.(*Config))
	}))
}
