package app.view;

import app.model.GameModel;
import app.controller.GameController;
import app.networks.CommunicationMessage;
import app.utilities.GamePlayersMaker;
import proto.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class GameMainMenu extends JPanel {
    JPanel box = new JPanel();
    JPanel ann = new JPanel();
    JPanel score = new JPanel();
    GameController gameController;
    GameModel gameModel;
    JButton buttonNewGame = new JButton("Новая игра");
    JButton updateAnn = new JButton("Обновить");

    public GameMainMenu(GameController gameController, GameModel gameModel) {
        this.gameModel = gameModel;
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        ann.setLayout(new BoxLayout(ann, BoxLayout.Y_AXIS));
        score.setLayout(new BoxLayout(score, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(200, 200));

        this.gameController = gameController;
        buttonNewGame.addActionListener((e) -> new NewGameSettingsMenu(gameController));
        updateAnn.addActionListener((e) -> gameController.updateOnlineGames());
        add(box);
        box.add(buttonNewGame);
        box.add(updateAnn);
        box.add(score);
        box.add(ann);
    }

    public void printScore() {
        char d = ' ';
        score.removeAll();
        for (SnakesProto.GamePlayer player : gameModel.getGameState().getPlayers().getPlayersList()) {
            if (gameModel.getSnakesAllCoordinatesByPlayer().containsKey(player.getId())) {
                if (player.getId() == gameModel.getSessionMasterId()) d = '*';
                score.add(new JLabel("playerId : [" + player.getId() + "]          score : " + gameModel.getSnakesAllCoordinatesByPlayer().get(player.getId()).size() + d));
            } else score.add(new JLabel("playerId : [" + player.getId() + "]          score : 0 VIEWER"));

        }
        validate();
    }

    public void printAvailableGames(ConcurrentHashMap<CommunicationMessage, Instant> announcement) {
        ann.removeAll();
        for (Map.Entry<CommunicationMessage, Instant> entry : announcement.entrySet()) {
            JPanel panel = new JPanel(new BorderLayout());
            JButton button = new JButton("Войти");
            JButton button1 = new JButton("Выйти");
            button1.addActionListener((e) -> gameController.exitFromGame());
            button.addActionListener((e) -> gameController.joinToPlayerGame(entry.getKey().getSenderPlayer()));
            JLabel label = new JLabel(Objects.requireNonNull(GamePlayersMaker.getMasterPlayerFromList(entry.getKey().getMessage().getAnnouncement().getPlayers())).getName()
                    + "    [" + entry.getKey().getSenderPlayer().getIpAddress() + "]    "
                    + entry.getKey().getMessage().getAnnouncement().getPlayers().getPlayersCount()
                    + "     " + entry.getKey().getMessage().getAnnouncement().getConfig().getWidth() + "x" + entry.getKey().getMessage().getAnnouncement().getConfig().getHeight()
                    + "     " + entry.getKey().getMessage().getAnnouncement().getConfig().getFoodStatic() + " + "
                    + entry.getKey().getMessage().getAnnouncement().getConfig().getFoodPerPlayer() + "x");

            panel.add(label, BorderLayout.WEST);
            if (entry.getKey().getSenderPlayer().getId() == gameModel.getSessionMasterId() && gameController.isMySnakeAlive())
                panel.add(button1, BorderLayout.EAST);
            else
                panel.add(button, BorderLayout.EAST);
            ann.add(panel);
            buttonNewGame.setSize(100, 50);
            validate();
        }
    }
}
