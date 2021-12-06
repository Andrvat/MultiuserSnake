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
            byte[] messageBytes;
            SnakesProto.GameMessage gameMessage;
            while (true) {
                try {
                    datagramSocket.receive(receivedPacket);
                    messageBytes = new byte[receivedPacket.getLength()];
                    System.arraycopy(receivedPacket.getData(), 0, messageBytes, 0, receivedPacket.getLength());
                    gameMessage = SnakesProto.GameMessage.parseFrom(messageBytes);
                    networkNode.handleReceivedUnicastMessage(gameMessage, receivedPacket.getAddress(), receivedPacket.getPort());
                    DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(), gameMessage.getTypeCase().toString());
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
