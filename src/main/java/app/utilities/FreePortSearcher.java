package app.utilities;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;

public class FreePortSearcher {
    private static final int MAX_INET_PORT_VALUE = 65535;

    public static int getRandomFreePort() {
        Random numbersGenerator = new Random();
        while (true) {
            int randomPort = numbersGenerator.nextInt(MAX_INET_PORT_VALUE);
            if (isPortFree(randomPort)) {
                return randomPort;
            }
        }
    }

    private static boolean isPortFree(int port) {
        try {
            new DatagramSocket(port).close();
            return true;
        } catch (SocketException e) {
            return false;
        }
    }
}
