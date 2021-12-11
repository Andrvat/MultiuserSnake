package app.view;

import app.model.GameModel;
import app.controller.GameController;
import app.networks.CommunicationMessage;
import app.utilities.GamePlayersMaker;
import proto.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class GameMainMenu extends JPanel {
    private final JPanel availableGamesPanel = new JPanel();
    private final JPanel scoresPanel = new JPanel();

    private static final Font LABELS_DEFAULT_FONT = new Font("Veranda", Font.BOLD, 20);
    private static final Font UPDATING_INFOS_FONT = new Font("Veranda", Font.BOLD, 16);

    private final GameController gameController;
    private final GameModel gameModel;

    private static final int LAYOUT_SIDE_PAD = 400;
    private static final int LAYOUT_NORTH_PAD = 40;

    public GameMainMenu(int widthScale, int heightScale,
                        GameController gameController, GameModel gameModel) {
        this.gameModel = gameModel;
        this.gameController = gameController;
        this.setPreferredSize(new Dimension(widthScale, heightScale));

        SpringLayout springLayout = new SpringLayout();
        this.setLayout(springLayout);
        JLabel menuLabel = new JLabel() {{
            setText("MAIN MENU");
            setFont(LABELS_DEFAULT_FONT);
        }};
        this.add(menuLabel);
        JLabel currentGameLabel = new JLabel() {{
            setText("CURRENT GAME");
            setFont(LABELS_DEFAULT_FONT);
        }};
        this.add(currentGameLabel);
        JLabel onlineGamesLabel = new JLabel() {{
            setText("ONLINE GAMES");
            setFont(LABELS_DEFAULT_FONT);
        }};
        this.add(onlineGamesLabel);
        JButton launchNewGameButton = new JButton("Start new game");
        this.add(launchNewGameButton);
        JButton updateOnlineGamesButton = new JButton("Update online games");
        this.add(updateOnlineGamesButton);
        this.add(scoresPanel);
        this.add(availableGamesPanel);

        availableGamesPanel.setLayout(new BoxLayout(availableGamesPanel, BoxLayout.Y_AXIS));
        scoresPanel.setLayout(new BoxLayout(scoresPanel, BoxLayout.Y_AXIS));

        launchNewGameButton.addActionListener(event -> new NewGameSettingsMenu(gameController));
        updateOnlineGamesButton.addActionListener(event -> gameController.updateOnlineGames());

        springLayout.putConstraint(SpringLayout.NORTH, menuLabel, LAYOUT_NORTH_PAD, SpringLayout.NORTH, this);
        springLayout.putConstraint(SpringLayout.WEST, menuLabel, LAYOUT_SIDE_PAD, SpringLayout.WEST, this);

        springLayout.putConstraint(SpringLayout.NORTH, launchNewGameButton, LAYOUT_NORTH_PAD, SpringLayout.NORTH, menuLabel);
        springLayout.putConstraint(SpringLayout.WEST, launchNewGameButton, LAYOUT_SIDE_PAD - 4, SpringLayout.WEST, this);

        springLayout.putConstraint(SpringLayout.NORTH, updateOnlineGamesButton, LAYOUT_NORTH_PAD, SpringLayout.NORTH, launchNewGameButton);
        springLayout.putConstraint(SpringLayout.WEST, updateOnlineGamesButton, LAYOUT_SIDE_PAD - 18, SpringLayout.WEST, this);

        springLayout.putConstraint(SpringLayout.NORTH, currentGameLabel, 2 * LAYOUT_NORTH_PAD, SpringLayout.NORTH, updateOnlineGamesButton);
        springLayout.putConstraint(SpringLayout.WEST, currentGameLabel, LAYOUT_SIDE_PAD - 18, SpringLayout.WEST, this);

        springLayout.putConstraint(SpringLayout.NORTH, scoresPanel, LAYOUT_NORTH_PAD, SpringLayout.NORTH, currentGameLabel);
        springLayout.putConstraint(SpringLayout.WEST, scoresPanel, LAYOUT_SIDE_PAD - 200, SpringLayout.WEST, this);

        springLayout.putConstraint(SpringLayout.NORTH, onlineGamesLabel, 10 * LAYOUT_NORTH_PAD, SpringLayout.NORTH, scoresPanel);
        springLayout.putConstraint(SpringLayout.WEST, onlineGamesLabel, LAYOUT_SIDE_PAD - 10, SpringLayout.WEST, this);

        springLayout.putConstraint(SpringLayout.NORTH, availableGamesPanel, LAYOUT_NORTH_PAD, SpringLayout.NORTH, onlineGamesLabel);
        springLayout.putConstraint(SpringLayout.WEST, availableGamesPanel, LAYOUT_SIDE_PAD - 180, SpringLayout.WEST, this);
    }

    public void printScore() {
        scoresPanel.removeAll();
        for (var player : gameModel.getGameState().getPlayers().getPlayersList()) {
            JLabel playerInfoLabel = new JLabel();
            playerInfoLabel.setFont(UPDATING_INFOS_FONT);
            if (gameModel.getSnakesAllCoordinatesByPlayer().containsKey(player.getId())) {
                int realScore = gameModel.getSnakesAllCoordinatesByPlayer().get(player.getId()).size() - 2;
                playerInfoLabel.setText("Player {" + player.getName() + "} with ID {" + player.getId() + "} " +
                        "has score: " + realScore + " {" + player.getRole() + "}");
            } else {
                playerInfoLabel.setText("Player {" + player.getName() + "} with ID {" + player.getId() + "} has score: 0" +
                        " {VIEWER}");
            }
            scoresPanel.add(playerInfoLabel);
        }
        this.validate();
    }

    public void printAvailableGames(ConcurrentHashMap<CommunicationMessage, Instant> availableGames) {
        availableGamesPanel.removeAll();
        for (var availableGame : availableGames.entrySet()) {
            JPanel rowPanel = new JPanel(new BorderLayout());

            JButton logInButton = new JButton("Log in");
            logInButton.addActionListener(event ->
                    gameController.joinToPlayerGame(availableGame.getKey().getSenderPlayer())
            );

            JButton logOutButton = new JButton("Log out");
            logOutButton.addActionListener(event ->
                    gameController.exitFromGame()
            );
            var masterPlayer = GamePlayersMaker.getMasterPlayerFromList(availableGame.getKey().getMessage().getAnnouncement().getPlayers());
            assert masterPlayer != null;
            JLabel infoLabel = new JLabel();
            infoLabel.setFont(UPDATING_INFOS_FONT);
            infoLabel.setText(masterPlayer.getName() +
                    " [IP: " +
                    availableGame.getKey().getSenderPlayer().getIpAddress() +
                    "] | " +
                    availableGame.getKey().getMessage().getAnnouncement().getPlayers().getPlayersCount() +
                    " | " +
                    availableGame.getKey().getMessage().getAnnouncement().getConfig().getWidth() +
                    "x" +
                    availableGame.getKey().getMessage().getAnnouncement().getConfig().getHeight() +
                    " | " +
                    availableGame.getKey().getMessage().getAnnouncement().getConfig().getFoodStatic() +
                    " + " +
                    availableGame.getKey().getMessage().getAnnouncement().getConfig().getFoodPerPlayer() +
                    "x    ");

            rowPanel.add(infoLabel, BorderLayout.WEST);
            if (availableGame.getKey().getSenderPlayer().getId() == gameModel.getSessionMasterId() &&
                    gameController.isMySnakeAlive()) {
                rowPanel.add(logOutButton, BorderLayout.EAST);
            } else {
                rowPanel.add(logInButton, BorderLayout.EAST);
            }
            availableGamesPanel.add(rowPanel);
        }
        this.validate();
    }
}
