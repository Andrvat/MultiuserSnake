package app.networks;

import lombok.Builder;
import proto.SnakesProto;

@Builder
public class CommunicationMessage {
    private SnakesProto.GameMessage message;
    private SnakesProto.GamePlayer senderPlayer;
    private SnakesProto.GamePlayer receiverPlayer;

    public SnakesProto.GamePlayer getSenderPlayer() {
        return senderPlayer;
    }

    public SnakesProto.GamePlayer getReceiverPlayer() {
        return receiverPlayer;
    }

    public SnakesProto.GameMessage getMessage() {
        return message;
    }

    public void setMessage(SnakesProto.GameMessage message) {
        this.message = message;
    }

    public void setReceiverPlayer(SnakesProto.GamePlayer receiverPlayer) {
        this.receiverPlayer = receiverPlayer;
    }

    @Override
    public String toString() {
        return "CommunicationMessage{" +
                "message=" + message +
                ", senderPlayer=" + senderPlayer +
                ", receiverPlayer=" + receiverPlayer +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CommunicationMessage message) {
            return senderPlayer.getPort() == message.senderPlayer.getPort();
        } else {
            return false;
        }
    }
}
