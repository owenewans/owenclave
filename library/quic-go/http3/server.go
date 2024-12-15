package http3

import (
	"github.com/xtls/quic-go/internal/protocol"
)

// NextProtoH3 is the ALPN protocol negotiated during the TLS handshake, for QUIC v1 and v2.
const NextProtoH3 = "h3"

// StreamType is the stream type of a unidirectional stream.
type StreamType uint64

const (
	streamTypeControlStream      = 0
	streamTypePushStream         = 1
	streamTypeQPACKEncoderStream = 2
	streamTypeQPACKDecoderStream = 3
)

func versionToALPN(v protocol.Version) string {
	//nolint:exhaustive // These are all the versions we care about.
	switch v {
	case protocol.Version1, protocol.Version2:
		return NextProtoH3
	default:
		return ""
	}
}
