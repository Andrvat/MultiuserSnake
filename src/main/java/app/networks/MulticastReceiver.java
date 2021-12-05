package app.networks;

import app.networks.NetworkNode;
import proto.SnakesProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

public class MulticastReceiver extends Thread {
    MulticastSocket socket;
    NetworkNode networkNode;

    public MulticastReceiver(MulticastSocket socket, NetworkNode networkNode) {
        this.socket = socket;
        this.networkNode = networkNode;
    }

    private void receiveGameMessage() throws IOException {
        byte[] receivedDataBuffer = new byte[10000];
        byte[] tmpBuffer;
        SnakesProto.GameMessage gameMessage;
        DatagramPacket packet = new DatagramPacket(receivedDataBuffer, receivedDataBuffer.length);
        while (true) {
            try {
                socket.receive(packet);

                tmpBuffer = new byte[packet.getLength()];
                if (packet.getLength() >= 0) System.arraycopy(packet.getData(), 0, tmpBuffer, 0, packet.getLength());

                gameMessage = SnakesProto.GameMessage.parseFrom(tmpBuffer);
                System.out.println(gameMessage.getTypeCase());

                networkNode.receiveMulticast(gameMessage, packet.getAddress(), packet.getPort());
            } catch (SocketTimeoutException ignored) {
            }
        }
    }

    @Override
    public void run() {
        try {
            receiveGameMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
