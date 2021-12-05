package app.view;

import app.model.GameModel;
import app.controller.GameController;
import proto.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;

public class GameField extends JPanel {
    private int WIDTH;
    private int HEIGHT;
    private int SCALE;
    private final GameModel gameModel;
    private final HashMap<Integer, Color> snakesColor = new HashMap<>();
    private final int id;

    public GameField(int weight, GameModel gameModel, GameController gameController, int id) {
        this.id = id;
        this.gameModel = gameModel;
        WIDTH = gameModel.getGameState().getConfig().getWidth();
        HEIGHT = gameModel.getGameState().getConfig().getHeight();
        SCALE = (int) Math.floor((float) weight / WIDTH);

        setPreferredSize(setFieldSize());
        setBorder(BorderFactory.createLineBorder(Color.red));
        KeyEventDispatcher ked = (e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if ((e.getKeyCode() == KeyEvent.VK_UP) || (e.getKeyCode() == KeyEvent.VK_W)) {
                    gameController.changeDirection(SnakesProto.Direction.UP);
                }
                if ((e.getKeyCode() == KeyEvent.VK_DOWN) || (e.getKeyCode() == KeyEvent.VK_S)) {
                    gameController.changeDirection(SnakesProto.Direction.DOWN);
                }
                if ((e.getKeyCode() == KeyEvent.VK_LEFT) || (e.getKeyCode() == KeyEvent.VK_A)) {
                    gameController.changeDirection(SnakesProto.Direction.LEFT);
                }
                if ((e.getKeyCode() == KeyEvent.VK_RIGHT) || (e.getKeyCode() == KeyEvent.VK_D)) {
                    gameController.changeDirection((SnakesProto.Direction.RIGHT));
                }
            }
            return false;
        });
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ked);
    }

    private Dimension setFieldSize() {
        return new Dimension(WIDTH * SCALE, HEIGHT * SCALE);
    }

    public void paint(Graphics g) {
        int width = WIDTH;
        WIDTH = gameModel.getGameState().getConfig().getWidth();
        HEIGHT = gameModel.getGameState().getConfig().getHeight();
        SCALE = (int) Math.floor((float) width * SCALE / WIDTH);
        paintField(g);
    }

    private void paintField(Graphics g) {
        g.setColor(Color.darkGray);
        g.fillRect(0, 0, WIDTH * SCALE, HEIGHT * SCALE);
        paintSnakes(g);
        paintFood(g);
        g.setColor(Color.BLACK);
        for (int x = 0; x < WIDTH * SCALE; x += SCALE) {
            g.drawLine(x, 0, x, HEIGHT * SCALE);
        }
        for (int y = 0; y < HEIGHT * SCALE; y += SCALE) {
            g.drawLine(0, y, WIDTH * SCALE, y);
        }
    }

    private void paintSnakes(Graphics g) {
        for (SnakesProto.GameState.Snake snake : gameModel.getGameState().getSnakesList()) {
            snakesColor.put(snake.getPlayerId(), Color.RED);
            paintSnake(snake, g);
        }
    }

    private void paintSnake(SnakesProto.GameState.Snake snake, Graphics g) {
        List<SnakesProto.GameState.Coord> list = gameModel.getSnakeAllCoordinates(snake);
        if (snake.getPlayerId() == id) g.setColor(Color.YELLOW);
        else g.setColor(Color.RED);
        for (SnakesProto.GameState.Coord coord : list) {
            g.fillRect(coord.getX() * SCALE + 1, coord.getY() * SCALE + 1, SCALE, SCALE);
        }
    }

    private void paintFood(Graphics g) {
        for (SnakesProto.GameState.Coord coord : gameModel.getGameState().getFoodsList()) {
            g.setColor(Color.GREEN);
            g.fillRect(coord.getX() * SCALE + 1, coord.getY() * SCALE + 1, SCALE - 1, SCALE - 1);
        }
    }
}