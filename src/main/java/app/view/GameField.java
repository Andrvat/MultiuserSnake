package app.view;

import app.model.GameModel;
import app.controller.GameController;
import proto.SnakesProto;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class GameField extends JPanel {
    private int fieldWidth;
    private int fieldHeight;
    private int widthRectScale;
    private int heightRectScale;
    private final GameModel gameModel;
    private final int ownerFieldId;

    public GameField(int widthScale, int heightScale,
                     GameModel gameModel, GameController gameController,
                     int ownerFieldId) {
        this.gameModel = gameModel;
        this.ownerFieldId = ownerFieldId;

        fieldWidth = gameModel.getGameState().getConfig().getWidth();
        fieldHeight = gameModel.getGameState().getConfig().getHeight();
        widthRectScale = (int) Math.floor((float) widthScale / fieldWidth);
        heightRectScale = (int) Math.floor((float) heightScale / fieldHeight);

        this.setPreferredSize(new Dimension(widthScale, heightScale));
        this.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        this.addUserStepsKeyDispatcher(gameController);
        this.setVisible(true);
    }

    private void addUserStepsKeyDispatcher(GameController gameController) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher((event -> {
            if (event.getID() == KeyEvent.KEY_PRESSED) {
                if (event.getKeyCode() == KeyEvent.VK_UP) {
                    gameController.changeDirection(SnakesProto.Direction.UP);
                }
                if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    gameController.changeDirection(SnakesProto.Direction.DOWN);
                }
                if (event.getKeyCode() == KeyEvent.VK_LEFT) {
                    gameController.changeDirection(SnakesProto.Direction.LEFT);
                }
                if (event.getKeyCode() == KeyEvent.VK_RIGHT) {
                    gameController.changeDirection((SnakesProto.Direction.RIGHT));
                }
            }
            return false;
        }));
    }


    @Override
    public void paint(Graphics g) {
        int lastWidth = fieldWidth;
        int lastHeight = fieldHeight;
        fieldWidth = gameModel.getGameState().getConfig().getWidth();
        fieldHeight = gameModel.getGameState().getConfig().getHeight();
        widthRectScale = (int) Math.floor((float) lastWidth * widthRectScale / fieldWidth);
        heightRectScale = (int) Math.floor((float) lastHeight * heightRectScale / fieldHeight);
        paintGameField(g);
    }

    private void paintGameField(Graphics graphics) {
        paintBackground(graphics);
        paintDelimiterLines(graphics);
        paintAllSnakes(graphics);
        paintFoods(graphics);
    }

    private void paintBackground(Graphics graphics) {
        graphics.setColor(Color.gray);
        graphics.fillRect(0, 0, this.getWidth(), this.getHeight());
    }

    private void paintAllSnakes(Graphics graphics) {
        for (var snake : gameModel.getGameState().getSnakesList()) {
            paintSnake(snake, graphics);
        }
    }

    private void paintSnake(SnakesProto.GameState.Snake snake, Graphics graphics) {
        var snakeAllCoordinates = gameModel.getSnakeAllCoordinates(snake);
        for (var coordinate : snakeAllCoordinates) {
            if (snake.getPlayerId() == ownerFieldId) {
                graphics.setColor(Color.ORANGE);
            } else {
                graphics.setColor(Color.CYAN);
            }
            if (snake.getPlayerId() == ownerFieldId && coordinate.equals(snakeAllCoordinates.getFirst())) {
                graphics.setColor(Color.YELLOW);
            }
            graphics.fillRect(coordinate.getX() * widthRectScale,
                    coordinate.getY() * heightRectScale,
                    widthRectScale, heightRectScale);
        }
    }

    private void paintFoods(Graphics graphics) {
        for (var foodCoordinate : gameModel.getGameState().getFoodsList()) {
            graphics.setColor(Color.RED);
            graphics.fillRect(foodCoordinate.getX() * widthRectScale,
                    foodCoordinate.getY() * heightRectScale,
                    widthRectScale, heightRectScale);
        }
    }

    private void paintDelimiterLines(Graphics graphics) {
        graphics.setColor(Color.BLACK);
        for (int x = 0; x < fieldWidth * widthRectScale; x += widthRectScale) {
            graphics.drawLine(x, 0, x, this.getHeight());
        }
        for (int y = 0; y < fieldHeight * heightRectScale; y += heightRectScale) {
            graphics.drawLine(0, y, this.getWidth(), y);
        }
    }
}