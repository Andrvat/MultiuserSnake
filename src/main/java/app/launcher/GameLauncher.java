package app.launcher;

import app.networks.NetworkNode;
import app.model.GameModel;
import app.utilities.CmdArgsParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.ParseException;
import proto.SnakesProto;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;
import java.util.UUID;

public class GameLauncher {
    public static void main(String[] args) {
        CmdArgsParser cmdArgsParser = new CmdArgsParser();
        try {
            cmdArgsParser.parseArguments(args);
            GameModel gameModel = new GameModel();
            NetworkNode networkNode = new NetworkNode(gameModel, SnakesProto.NodeRole.NORMAL,
                    cmdArgsParser.getPlayerName(),
                    InetAddress.getByName(cmdArgsParser.getHostInetAddress()),
                    cmdArgsParser.getHostPort(), UUID.randomUUID());
            networkNode.start();
        } catch (Exception exception) {
            System.err.println(cmdArgsParser);
            exception.printStackTrace();
        }
    }
}
