package app.utilities;

import proto.SnakesProto;

public class GamePlayersMaker {

    public static SnakesProto.GamePlayer getMasterPlayerFromList(SnakesProto.GamePlayers activePlayers) {
        var masterPlayerIndicator = SnakesProto.NodeRole.MASTER;
        for (var player : activePlayers.getPlayersList()) {
            if (player.getRole().equals(masterPlayerIndicator)) {
                return player;
            }
        }
        return null;
    }

    public static SnakesProto.GamePlayer getDeputyPlayerFromList(SnakesProto.GamePlayers activePlayers) {
        var deputyPlayerIndicator = SnakesProto.NodeRole.DEPUTY;
        for (var player : activePlayers.getPlayersList()) {
            if (player.getRole().equals(deputyPlayerIndicator)) {
                return player;
            }
        }
        return null;
    }

    public static SnakesProto.GamePlayer buildGamePlayerImage(
            int playerId, String playerName, int inetPort, String
            inetAddress, SnakesProto.NodeRole playerRole) {
        return SnakesProto.GamePlayer.newBuilder()
                .setId(playerId)
                .setName(playerName)
                .setPort(inetPort)
                .setRole(playerRole)
                .setIpAddress(inetAddress)
                .setScore(0)
                .build();
    }
}
