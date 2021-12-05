package app.utilities;

import proto.SnakesProto;

public class CommunicationMessage {
    public SnakesProto.GameMessage gameMessage;
    public SnakesProto.GamePlayer from;
    public SnakesProto.GamePlayer to;

    public CommunicationMessage(SnakesProto.GameMessage m, SnakesProto.GamePlayer from, SnakesProto.GamePlayer to) {
        this.gameMessage = m;
        this.from = from;
        this.to = to;
    }

    public SnakesProto.GamePlayer getFrom() {
        return from;
    }

    public SnakesProto.GamePlayer getTo() {
        return to;
    }

    public SnakesProto.GameMessage getGameMessage() {
        return gameMessage;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CommunicationMessage m) {
            return from.getPort() == m.from.getPort();
        } else {
            return false;
        }
    }
}
