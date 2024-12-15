package quic

import (
	"github.com/xtls/quic-go/internal/protocol"
	"github.com/xtls/quic-go/internal/qerr"
)

// packetHandler handles packets
type packetHandler interface {
	handlePacket(receivedPacket)
	destroy(error)
	closeWithTransportError(qerr.TransportErrorCode)
}

type packetHandlerManager interface {
	Get(protocol.ConnectionID) (packetHandler, bool)
	GetByResetToken(protocol.StatelessResetToken) (packetHandler, bool)
	AddWithConnID(destConnID, newConnID protocol.ConnectionID, h packetHandler) bool
	Close(error)
	connRunner
}

type quicConn interface {
	EarlyConnection
	earlyConnReady() <-chan struct{}
	handlePacket(receivedPacket)
	run() error
	destroy(error)
	closeWithTransportError(TransportErrorCode)
}
