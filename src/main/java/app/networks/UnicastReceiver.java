package app.networks;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

import app.utilities.DebugPrinter;
import lombok.Builder;
import proto.SnakesProto;

@Builder
public class UnicastReceiver extends Thread {
    private static final int RECEIVE_BUFFER_SIZE = 10000;
    private final DatagramSocket datagramSocket;
    private final NetworkNode networkNode;

    @Override
    public void run() {
        try {
            byte[] receivedDataBuffer = new byte[RECEIVE_BUFFER_SIZE];
            DatagramPacket receivedPacket = new DatagramPacket(receivedDataBuffer, receivedDataBuffer.length);
            SnakesProto.GameMessage gameMessage;
            byte[] messageBytes;
            while (true) {
                try {
                    datagramSocket.receive(receivedPacket);
                    messageBytes = new byte[receivedPacket.getLength()];
                    System.arraycopy(receivedPacket.getData(), 0, messageBytes, 0, receivedPacket.getLength());
                    gameMessage = SnakesProto.GameMessage.parseFrom(messageBytes);
                    DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(),
                            "Got new game message with type " + gameMessage.getTypeCase());
                    networkNode.handleReceivedUnicastMessage(gameMessage, receivedPacket.getAddress(), receivedPacket.getPort());
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
