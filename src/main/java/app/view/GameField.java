package app.view;

import app.model.GameModel;
import app.controller.GameController;
import proto.SnakesProto;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class GameField extends JPanel {
    private int fieldWidth;
    private int fieldHeight;
    private int rectScale;
    private final GameModel gameModel;
    private final int ownerFieldId;

    public GameField(int scalePart, GameModel gameModel, GameController gameController, int ownerFieldId) {
        this.ownerFieldId = ownerFieldId;
        this.gameModel = gameModel;
        fieldWidth = gameModel.getGameState().getConfig().getWidth();
        fieldHeight = gameModel.getGameState().getConfig().getHeight();
        rectScale = (int) Math.floor((float) scalePart / fieldWidth);

        setPreferredSize(new Dimension(fieldWidth * rectScale, fieldHeight * rectScale));
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

    @Override
    public void paint(Graphics g) {
        int width = fieldWidth;
        fieldWidth = gameModel.getGameState().getConfig().getWidth();
        fieldHeight = gameModel.getGameState().getConfig().getHeight();
        rectScale = (int) Math.floor((float) width * rectScale / fieldWidth);
        paintGameField(g);
    }

    private void paintGameField(Graphics graphics) {
        paintBackground(graphics);
        paintAllSnakes(graphics);
        paintFoods(graphics);
        paintDelimiterLines(graphics);
    }

    private void paintBackground(Graphics graphics) {
        graphics.setColor(Color.darkGray);
        graphics.fillRect(0, 0, fieldWidth * rectScale, fieldHeight * rectScale);
    }

    private void paintAllSnakes(Graphics graphics) {
        for (var snake : gameModel.getGameState().getSnakesList()) {
            paintSnake(snake, graphics);
        }
    }

    private void paintSnake(SnakesProto.GameState.Snake snake, Graphics graphics) {
        var snakeAllCoordinates = gameModel.getSnakeAllCoordinates(snake);
        for (var coordinate : snakeAllCoordinates) {
            graphics.setColor(Color.ORANGE);
            if (snake.getPlayerId() == ownerFieldId && coordinate.equals(snakeAllCoordinates.getFirst())) {
                graphics.setColor(Color.YELLOW);
            }
            graphics.fillRect(coordinate.getX() * rectScale,
                    coordinate.getY() * rectScale,
                    rectScale, rectScale);
        }
    }

    private void paintFoods(Graphics graphics) {
        for (var foodCoordinate : gameModel.getGameState().getFoodsList()) {
            graphics.setColor(Color.RED);
            graphics.fillRect(foodCoordinate.getX() * rectScale,
                    foodCoordinate.getY() * rectScale,
                    rectScale, rectScale);
        }
    }

    private void paintDelimiterLines(Graphics graphics) {
        graphics.setColor(Color.BLACK);
        for (int x = 0; x < fieldWidth * rectScale; x += rectScale) {
            graphics.drawLine(x, 0, x, fieldHeight * rectScale);
        }
        for (int y = 0; y < fieldHeight * rectScale; y += rectScale) {
            graphics.drawLine(0, y, fieldWidth * rectScale, y);
        }
    }
}