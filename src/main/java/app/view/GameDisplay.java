package app.view;

import app.model.GameModel;
import app.controller.GameController;
import app.networks.CommunicationMessage;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class GameDisplay extends JFrame {
    private final GameField gameField;
    private final MainMenu mainMenu;

    public GameDisplay(int w, int h, int c_w, int c_h, GameController gameController, GameModel gameModel, int id) {
        setSize(new Dimension(w + 10, h + 30));
        setLayout(new BorderLayout());
        setTitle("Snake");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        gameField = new GameField(w / 2, gameModel, gameController, id);
        mainMenu = new MainMenu(gameController, gameModel);
        add(gameField, BorderLayout.WEST);
        add(mainMenu, BorderLayout.EAST);

        setVisible(true);
    }

    public void update() {
        gameField.setFocusable(true);
        gameField.requestFocusInWindow();
        mainMenu.printScore();
        gameField.repaint();
    }

    public void updateAnn(ConcurrentHashMap<CommunicationMessage, Instant> ann) {
        mainMenu.printAnn(ann);
    }
}
