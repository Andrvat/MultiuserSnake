package app.view;

import app.model.GameModel;
import app.networks.NetworkNode;
import app.controller.GameController;
import app.utilities.CommunicationMessage;
import app.utilities.Subscriber;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ViewController extends Subscriber {
    NetworkNode networkNode;
    GameDisplay gameDisplay;
    GameController gameController;
    int w = 1600;
    int h = w / 2;

    public ViewController(GameModel gameModel, NetworkNode networkNode){
        super(gameModel);
        this.networkNode = networkNode;
        gameController = new GameController(gameModel, networkNode);
        gameDisplay = new GameDisplay(w, h, gameModel.getWidth(), gameModel.getHeight(), gameController, gameModel, networkNode.nodeID.hashCode());
    }

    @Override
    public void Notify(int x){
        gameDisplay.update();
    }

    public void updateAnn(ConcurrentHashMap<CommunicationMessage, Instant> ann){
        gameDisplay.updateAnn(ann);
    }
}
