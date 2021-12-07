package app.view;

import app.model.GameModel;
import app.networks.NetworkNode;
import app.controller.GameController;
import app.networks.CommunicationMessage;
import app.utilities.notifications.Subscriber;
import lombok.Builder;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ViewController extends Subscriber {
    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;

    private final GameMainDisplay gameMainDisplay;

    @Builder
    public ViewController(GameModel gameModel, NetworkNode networkNode) {
        super(gameModel);
        GameController gameController = GameController.builder()
                .gameModel(gameModel)
                .networkNode(networkNode)
                .build();
        this.gameMainDisplay = new GameMainDisplay(SCREEN_WIDTH, SCREEN_HEIGHT,
                gameController, gameModel, networkNode.getNodeId().hashCode());
    }

    @Override
    public void updateState() {
        gameMainDisplay.updateDisplay();
    }

    public void updateAvailableGames(ConcurrentHashMap<CommunicationMessage, Instant> availableGames) {
        gameMainDisplay.updateGames(availableGames);
    }
}
