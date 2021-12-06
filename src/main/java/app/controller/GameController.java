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

//    public void newGame() {
//        SnakesProto.GameConfig gameConfig = SnakesProto.GameConfig.newBuilder()
//                .setWidth(40)
//                .setHeight(30)
//                .setFoodStatic(50)
//                .setFoodPerPlayer((float) 0.2)
//                .setStateDelayMs(500)
//                .setDeadFoodProb((float) 0.8)
//                .setPingDelayMs(100)
//                .setNodeTimeoutMs(9000)
//                .build();
//        gameModel.launchNewGameAsMaster(gameConfig, networkNode.getNodeName(), networkNode.getNodeId().hashCode(), networkNode.getMyPort());
//        networkNode.setNewMasterPlayer(SnakesProto.NodeRole.MASTER);
//        System.out.println("NEW GAME!");
//    }

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
        gameModel.launchNewGameAsMaster(gameConfig, networkNode.getNodeName(), networkNode.getNodeId().hashCode(), networkNode.getMyPort());
        networkNode.setNewDefaultMasterPlayer();
        System.out.println("NEW GAME!");
    }

    public void changeDirection(SnakesProto.Direction direction) {
        networkNode.sendChangeSnakeDirection(direction);
    }

    public void nModelSub() {
        gameModel.informAllSubscribers();
    }

    public void join(SnakesProto.GamePlayer player) {
        networkNode.sendJoinGameMessage(player);
    }

    public void exit() {
        networkNode.sendRoleChangeMessage(null, SnakesProto.NodeRole.VIEWER, SnakesProto.NodeRole.VIEWER);
    }

    public boolean checkAlive() {
        return gameModel.isPlayerSnakeAlive(networkNode.getNodeId().hashCode());
    }
}
