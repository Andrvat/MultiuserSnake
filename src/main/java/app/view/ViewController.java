package app.view;

import app.model.GameModel;
import app.networks.NetworkNode;
import app.controller.GameController;
import app.networks.CommunicationMessage;
import app.utilities.notifications.Subscriber;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ViewController extends Subscriber {
    NetworkNode networkNode;
    GameDisplay gameDisplay;
    GameController gameController;
    int w = 1600;
    int h = w / 2;

    public ViewController(GameModel gameModel, NetworkNode networkNode) {
        super(gameModel);
        this.networkNode = networkNode;
        gameController = new GameController(gameModel, networkNode);
        gameDisplay = new GameDisplay(w, h, gameModel.getWidthFromGameConfig(), gameModel.getHeightFromGameConfig(), gameController, gameModel, networkNode.getNodeId().hashCode());
    }

    @Override
    public void update() {
        gameDisplay.update();
    }

    public void updateAnn(ConcurrentHashMap<CommunicationMessage, Instant> ann) {
        gameDisplay.updateAnn(ann);
    }
}
