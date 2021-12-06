package app.networks;

import app.utilities.DebugPrinter;
import lombok.Builder;
import proto.SnakesProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

@Builder
public class MulticastReceiver extends Thread {
    private static final int RECEIVE_BUFFER_SIZE = 10000;
    private final MulticastSocket multicastSocket;
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
                    multicastSocket.receive(receivedPacket);

                    messageBytes = new byte[receivedPacket.getLength()];
                    System.arraycopy(receivedPacket.getData(), 0, messageBytes, 0, receivedPacket.getLength());

                    gameMessage = SnakesProto.GameMessage.parseFrom(messageBytes);
                    networkNode.handleReceivedMulticastMessage(gameMessage, receivedPacket.getAddress(), receivedPacket.getPort());
                    DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(), gameMessage.getTypeCase().toString());
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
