package app.view;

import app.model.GameModel;
import app.controller.GameController;
import app.networks.CommunicationMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

public class GameMainDisplay extends JFrame {
    private static final String GAME_NAME = "ONLINE SNAKE | " + LocalDateTime.now();

    private final GameField gameField;
    private final GameMainMenu gameMainMenu;

    private static final int LAYOUT_SIDE_PAD = 30;
    private static final int LAYOUT_NORTH_PAD = 30;

    private static final int SCREEN_WIDTH_DELIMITER_FACTOR = 2;
    private static final int SCREEN_HEIGHT_DELIMITER_FACTOR = 10;
    private static final int SCREEN_HEIGHT_MULTIPLAYER_FACTOR = 9;

    public GameMainDisplay(int screenWidth, int screenHeight,
                           GameController gameController, GameModel gameModel,
                           int ownerNodeId) {
        gameField = new GameField(
                screenWidth / SCREEN_WIDTH_DELIMITER_FACTOR,
                screenHeight / SCREEN_HEIGHT_DELIMITER_FACTOR * SCREEN_HEIGHT_MULTIPLAYER_FACTOR,
                gameModel, gameController, ownerNodeId);
        gameMainMenu = new GameMainMenu(
                screenWidth / SCREEN_WIDTH_DELIMITER_FACTOR,
                screenHeight / SCREEN_HEIGHT_DELIMITER_FACTOR * SCREEN_HEIGHT_MULTIPLAYER_FACTOR,
                gameController, gameModel);

        this.setSize(new Dimension(screenWidth, screenHeight));
        this.setTitle(GAME_NAME);
        this.setResizable(false);
        this.setScreenListenerForCloseOperation();

        Container contentPane = this.getContentPane();
        SpringLayout springLayout = new SpringLayout();
        contentPane.setLayout(springLayout);

        contentPane.add(gameField);
        contentPane.add(gameMainMenu);

        springLayout.putConstraint(SpringLayout.NORTH, gameField, LAYOUT_NORTH_PAD, SpringLayout.NORTH, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameMainMenu, LAYOUT_NORTH_PAD, SpringLayout.NORTH, contentPane);
        springLayout.putConstraint(SpringLayout.WEST, gameField, LAYOUT_SIDE_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.EAST, gameMainMenu, LAYOUT_SIDE_PAD, SpringLayout.EAST, contentPane);

        this.setVisible(true);
    }

    private void setScreenListenerForCloseOperation() {
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        JFrame thisFrame = this;
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int chosenOption =
                        JOptionPane.showConfirmDialog(thisFrame,
                                "Are you sure?",
                                "Exit",
                                JOptionPane.YES_NO_OPTION);
                if (isYesChosen(chosenOption)) {
                    System.exit(0);
                }
            }

            private boolean isYesChosen(int index) {
                return index == 0;
            }
        });
    }

    public void updateDisplay() {
        gameField.setFocusable(true);
        gameField.requestFocusInWindow();
        gameMainMenu.printScore();
        gameField.repaint();
    }

    public void updateGames(ConcurrentHashMap<CommunicationMessage, Instant> availableGames) {
        gameMainMenu.printAvailableGames(availableGames);
    }
}
