package router

import (
	"context"
	"sync"

	"github.com/exclavenetwork/exclave-core/v5/features/extension"
	"github.com/exclavenetwork/exclave-core/v5/features/outbound"
)

type BalancingStrategy interface {
	PickOutbound([]string) string
}

type BalancingPrincipleTarget interface {
	GetPrincipleTarget([]string) []string
}

type connectionResetter interface {
	ResetConnections()
}

func resetOutboundConnections(ohm outbound.Manager, tag string) {
	if ohm == nil || tag == "" {
		return
	}
	if h := ohm.GetHandler(tag); h != nil {
		if r, ok := h.(connectionResetter); ok {
			r.ResetConnections()
		}
	}
}

type Balancer struct {
	selectors   []string
	strategy    BalancingStrategy
	ohm         outbound.Manager
	fallbackTag string

	override override

	stateMu sync.Mutex
	lastTag string
}

// PickOutbound picks the tag of an outbound
func (b *Balancer) PickOutbound() (string, error) {
	candidates, err := b.SelectOutbounds()
	if err != nil {
		if b.fallbackTag != "" {
			b.recordPicked(b.fallbackTag)
			newError("fallback to [", b.fallbackTag, "], due to error: ", err).AtInfo().WriteToLog()
			return b.fallbackTag, nil
		}
		if len(candidates) == 0 {
			return "", err
		}
		b.recordPicked(candidates[0])
		newError("fallback to the first tag [", candidates[0], "]").Base(err).AtInfo().WriteToLog()
		return candidates[0], nil
	}
	var tag string
	if o := b.override.Get(); o != "" {
		tag = o
	} else {
		tag = b.strategy.PickOutbound(candidates)
	}
	if tag == "" {
		if b.fallbackTag != "" {
			b.recordPicked(b.fallbackTag)
			newError("fallback to [", b.fallbackTag, "], due to empty tag returned").AtInfo().WriteToLog()
			return b.fallbackTag, nil
		}
		if len(candidates) == 0 {
			return "", newError("balancing strategy returns empty tag")
		}
		b.recordPicked(candidates[0])
		err := newError("balancing strategy returns empty tag")
		newError("fallback to the first tag [", candidates[0], "]").Base(err).AtInfo().WriteToLog()
		return candidates[0], nil
	}
	b.recordPicked(tag)
	return tag, nil
}

func (b *Balancer) recordPicked(tag string) {
	b.stateMu.Lock()
	prev := b.lastTag
	b.lastTag = tag
	b.stateMu.Unlock()

	if prev != "" && prev == b.fallbackTag && tag != b.fallbackTag {
		go resetOutboundConnections(b.ohm, prev)
	}
}

func (b *Balancer) InjectContext(ctx context.Context) {
	if contextReceiver, ok := b.strategy.(extension.ContextReceiver); ok {
		contextReceiver.InjectContext(ctx)
	}
}

// SelectOutbounds select outbounds with selectors of the Balancer
func (b *Balancer) SelectOutbounds() ([]string, error) {
	hs, ok := b.ohm.(outbound.HandlerSelector)
	if !ok {
		return nil, newError("outbound.Manager is not a HandlerSelector")
	}
	tags := hs.Select(b.selectors)
	return tags, nil
}

// GetPrincipleTarget implements routing.BalancerPrincipleTarget
func (r *Router) GetPrincipleTarget(tag string) ([]string, error) {
	if b, ok := r.balancers[tag]; ok {
		if s, ok := b.strategy.(BalancingPrincipleTarget); ok {
			candidates, err := b.SelectOutbounds()
			if err != nil {
				return nil, newError("unable to select outbounds").Base(err)
			}
			return s.GetPrincipleTarget(candidates), nil
		}
		return nil, newError("unsupported GetPrincipleTarget")
	}
	return nil, newError("cannot find tag")
}

// SetOverrideTarget implements routing.BalancerOverrider
func (r *Router) SetOverrideTarget(tag, target string) error {
	if b, ok := r.balancers[tag]; ok {
		b.override.Put(target)
		return nil
	}
	return newError("cannot find tag")
}

// GetOverrideTarget implements routing.BalancerOverrider
func (r *Router) GetOverrideTarget(tag string) (string, error) {
	if b, ok := r.balancers[tag]; ok {
		return b.override.Get(), nil
	}
	return "", newError("cannot find tag")
}

