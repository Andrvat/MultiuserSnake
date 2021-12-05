package app.controller;

import app.networks.NetworkNode;
import app.model.GameModel;
import proto.SnakesProto;

public class GameController {
    private final GameModel gameModel;
    private final NetworkNode networkNode;

    public GameController(GameModel gameModel, NetworkNode networkNode) {
        this.gameModel = gameModel;
        this.networkNode = networkNode;
    }

    public void newGame() {
        SnakesProto.GameConfig gameConfig = SnakesProto.GameConfig.newBuilder()
                .setWidth(40)
                .setHeight(30)
                .setFoodStatic(50)
                .setFoodPerPlayer((float) 0.2)
                .setStateDelayMs(500)
                .setDeadFoodProb((float) 0.8)
                .setPingDelayMs(100)
                .setNodeTimeoutMs(9000)
                .build();
        gameModel.newGameAsMaster(gameConfig, networkNode.getNodeName(), networkNode.nodeID.hashCode(), networkNode.getMyPort());
        networkNode.changeRole(SnakesProto.NodeRole.MASTER);
        System.out.println("NEW GAME!");
    }

    public void newGame(int w, int h, int fs, float fp, int delay) {
        SnakesProto.GameConfig gameConfig = SnakesProto.GameConfig.newBuilder()
                .setWidth(w)
                .setHeight(h)
                .setFoodStatic(fs)
                .setFoodPerPlayer(fp)
                .setStateDelayMs(delay)
                .setDeadFoodProb((float) 0.8)
                .setPingDelayMs(100)
                .setNodeTimeoutMs(9000)
                .build();
        gameModel.newGameAsMaster(gameConfig, networkNode.getNodeName(), networkNode.nodeID.hashCode(), networkNode.getMyPort());
        networkNode.changeRole(SnakesProto.NodeRole.MASTER);
        System.out.println("NEW GAME!");
    }

    public void changeDirection(SnakesProto.Direction direction) {
        networkNode.changeDirection(direction);
    }

    public void nModelSub(int x) {
        gameModel.NotifyAll(x);
    }

    public void join(SnakesProto.GamePlayer player, SnakesProto.GameConfig config) {
        networkNode.sendJoinGame(player, config);
    }

    public void exit() {
        networkNode.sendChangeRoleMsg(null, SnakesProto.NodeRole.VIEWER, SnakesProto.NodeRole.VIEWER);
    }

    public boolean checkAlive() {
        return gameModel.isAlive(networkNode.nodeID.hashCode());
    }
}
