package app.controller;

import app.networks.NetworkNode;
import app.model.GameModel;
import app.utilities.DebugPrinter;
import lombok.Builder;
import proto.SnakesProto;

@Builder
public class GameController {
    private final GameModel gameModel;
    private final NetworkNode networkNode;

    public void launchNewGame(int gameWidth, int gameHeight,
                              int gameFoodStatic, float gameFoodPerPlayer,
                              int stateDelay, float gameDeadFoodProb,
                              int gamePingDelayMs, int gameNodeTimeoutMs) {
        var gameConfig = SnakesProto.GameConfig.newBuilder()
                .setWidth(gameWidth)
                .setHeight(gameHeight)
                .setFoodStatic(gameFoodStatic)
                .setFoodPerPlayer(gameFoodPerPlayer)
                .setStateDelayMs(stateDelay)
                .setDeadFoodProb(gameDeadFoodProb)
                .setPingDelayMs(gamePingDelayMs)
                .setNodeTimeoutMs(gameNodeTimeoutMs)
                .build();
        gameModel.launchNewGameAsMaster(gameConfig, networkNode.getNodeName(),
                networkNode.getNodeId().hashCode(), networkNode.getMyPort());
        networkNode.setNewDefaultMasterPlayer();
        DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(), "New game");
    }

    public void changeDirection(SnakesProto.Direction direction) {
        networkNode.sendChangeSnakeDirection(direction);
    }

    public void updateOnlineGames() {
        gameModel.informAllSubscribers();
    }

    public void joinToPlayerGame(SnakesProto.GamePlayer gameOwner) {
        networkNode.sendJoinGameMessage(gameOwner);
    }

    public void exitFromGame() {
        networkNode.handleLogoutAction();
    }

    public boolean isMySnakeAlive() {
        return gameModel.isPlayerSnakeAlive(networkNode.getNodeId().hashCode());
    }
}
