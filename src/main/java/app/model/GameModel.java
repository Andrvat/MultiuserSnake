package app.model;

import app.utilities.Publisher;
import proto.SnakesProto;

import java.time.Instant;
import java.util.*;

import static java.lang.Math.abs;

public class GameModel extends Publisher {
    private static final int INITIAL_STATE_ORDER = 0;

    private HashMap<Integer, Long> changeHelper = new HashMap<>();
    private HashMap<Integer, Instant> playerActivitiesTimestamps = new HashMap<>();
    private HashMap<Integer, LinkedList<SnakesProto.GameState.Coord>> snakeCoordinates = new HashMap<>();
    private HashMap<Integer, SnakesProto.Direction> changeDirections = new HashMap<>();
    private SnakesProto.GamePlayers sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
    private SnakesProto.GameState gameState;
    private ModelState modelState = ModelState.OUT_OF_GAME;
    private int sessionMasterId;

    public GameModel() {
        changeGameStateBy(getDefaultGameConfig());
    }

    private SnakesProto.GameConfig getDefaultGameConfig() {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(10)
                .setHeight(10)
                .setFoodStatic(10)
                .setFoodPerPlayer((float) 0.2)
                .setStateDelayMs(1000)
                .setDeadFoodProb((float) 0.2)
                .setPingDelayMs(3000)
                .setNodeTimeoutMs(9000)
                .build();
    }

    private void changeGameStateBy(SnakesProto.GameConfig gameConfig) {
        gameState = SnakesProto.GameState.newBuilder()
                .setConfig(gameConfig)
                .setPlayers(sessionGamePlayers)
                .setStateOrder(INITIAL_STATE_ORDER)
                .build();
    }

    public int getWidth() {
        return gameState.getConfig().getWidth();
    }

    public int getHeight() {
        return gameState.getConfig().getHeight();
    }

    public SnakesProto.GamePlayers getSessionGamePlayers() {
        return sessionGamePlayers;
    }

    public HashMap<Integer, Long> getChangeHelper() {
        return changeHelper;
    }

    public HashMap<Integer, Instant> getPlayerActivitiesTimestamps() {
        return playerActivitiesTimestamps;
    }

    public SnakesProto.GameConfig getConfig() {
        return gameState.getConfig();
    }

    public ModelState getState() {
        return modelState;
    }

    public void setMasterIp(int address) {
        sessionMasterId = address;
    }

    public SnakesProto.GameState getGameState() {
        return gameState;
    }

    public HashMap<Integer, LinkedList<SnakesProto.GameState.Coord>> getSnakeCoordinates() {
        return snakeCoordinates;
    }

    public int getSessionMasterId() {
        return sessionMasterId;
    }

    public int countAlive() {
        int number = 0;
        for (SnakesProto.GameState.Snake shake : gameState.getSnakesList()) {
            if (shake.getState() == SnakesProto.GameState.Snake.SnakeState.ALIVE)
                number++;
        }
        return number;
    }

    public void newGameAsMaster(SnakesProto.GameConfig gameConfig, String name, int id, int port) {
        sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
        snakeCoordinates = new HashMap<>();
        changeDirections = new HashMap<>();
        changeHelper = new HashMap<>();
        playerActivitiesTimestamps = new HashMap<>();
        changeGameStateBy(gameConfig);
        SnakesProto.GamePlayer self = SnakesProto.GamePlayer.newBuilder()
                .setId(id)
                .setName(name)
                .setPort(port)
                .setRole(SnakesProto.NodeRole.MASTER)
                .setIpAddress("")
                .setScore(0)
                .build();
        sessionMasterId = id;
        modelState = ModelState.IN_GAME;

        addPlayer(self);

        NotifyAll(1);
    }

    public void setGameState(SnakesProto.GameState gameState) {
        this.gameState = gameState;
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            snakeCoordinates.put(snake.getPlayerId(), snakeToList(snake));
        }
        NotifyAll(1);
    }

    public SnakesProto.GameState.Snake addSnake(int id) {
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == id) return null;
        }
        SnakesProto.GameState.Snake.Builder snakeBuilder = SnakesProto.GameState.Snake.newBuilder();
        LinkedList<SnakesProto.GameState.Coord> coords = findEmptyPlace();
        if (coords == null) return null;
        snakeBuilder.addPoints(coords.get(0));
        snakeBuilder.addPoints(coords.get(1));

        SnakesProto.Direction direction = SnakesProto.Direction.DOWN;
        SnakesProto.GameState.Snake snake = snakeBuilder
                .setPlayerId(id)
                .setHeadDirection(direction)
                .setState(SnakesProto.GameState.Snake.SnakeState.ALIVE)
                .build();
        gameState = gameState.toBuilder().addSnakes(snake).build();
        changeDirections.put(snake.getPlayerId(), snake.getHeadDirection());
        changeHelper.put(snake.getPlayerId(), Long.parseLong("0"));
        return snakeBuilder.build();
    }

    public int addPlayer(SnakesProto.GamePlayer player) {
        SnakesProto.GameState.Snake s = null;
        boolean ingame = false;
        for (SnakesProto.GamePlayer player1 : gameState.getPlayers().getPlayersList()) {
            if (player1.getId() == player.getId()) {
                ingame = true;
                changeRole(player.getId(), SnakesProto.NodeRole.NORMAL, SnakesProto.GameState.Snake.SnakeState.ALIVE);
            }
        }
        if (player.getRole() != SnakesProto.NodeRole.VIEWER) {
            s = addSnake(player.getId());
            if (s != null) {
                snakeCoordinates.put(player.getId(), snakeToList(s));
            }
        }
        if (s == null) {
            player = player.toBuilder().setRole(SnakesProto.NodeRole.VIEWER).build();
        }
        if (!ingame) {
            sessionGamePlayers = gameState.getPlayers().toBuilder().addPlayers(player).build();
            gameState = gameState.toBuilder().setPlayers(sessionGamePlayers).build();
        }
        if (s == null) return 2;

        return 0;
    }

    private LinkedList<SnakesProto.GameState.Coord> findEmptyPlace() {
        Random random = new Random();
        boolean isjoinable = false;
        int x = random.nextInt() % getWidth();
        int y = random.nextInt() % getHeight();
        for (int i = x; i < x + getWidth(); i++) {
            for (int j = y; j < y + getHeight(); j++) {
                if (isFreeRectangle(i, j, i + 4, j + 4)) {
                    isjoinable = true;
                    break;
                }
            }
        }
        if (!isjoinable) return null;

        LinkedList<SnakesProto.GameState.Coord> coords = new LinkedList<>();
        SnakesProto.GameState.Coord coord1 = makeCoord(x + 2, y + 2);
        SnakesProto.GameState.Coord coord2 = makeCoord(0, 1);

        coords.add(coord1);
        coords.add(coord2);

        return coords;
    }

    private boolean isFreeRectangle(int x1, int x2, int y1, int y2) {
        for (int i = x1; i < x2; i++) {
            for (int j = y1; j < y2; j++) {
                for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
                    for (SnakesProto.GameState.Coord coord : snake.getPointsList()) {
                        if (makeCoord(i, j).equals(coord)) return false;
                    }
                }
            }
        }
        return true;
    }

    public void addFood() {
        Vector<SnakesProto.GameState.Coord> freePlaces = new Vector<>();
        for (int i = 0; i < getWidth(); i++) {
            for (int j = 0; j < getHeight(); j++) {
                SnakesProto.GameState.Coord coord1 = makeCoord(i, j);
                for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
                    for (SnakesProto.GameState.Coord coord : snake.getPointsList()) {
                        if (!coord1.equals(coord)) freePlaces.add(makeCoord(i, j));
                    }
                }
                for (SnakesProto.GameState.Coord coord : gameState.getFoodsList()) {
                    if (!coord1.equals(coord)) {
                        freePlaces.add(coord1);
                    }
                }
            }
        }
        SnakesProto.GameState.Builder builder = gameState.toBuilder();
        int foodCount = gameState.getFoodsCount();
        while (foodCount < gameState.getConfig().getFoodStatic() + gameState.getConfig().getFoodPerPlayer() * countAlive()) {
            SnakesProto.GameState.Coord food = tryAddFruit(freePlaces);
            if (food == null) break;

            builder.addFoods(food);
            freePlaces.remove(food);
            foodCount++;
        }
        gameState = builder.build();
    }

    public SnakesProto.GameState.Coord tryAddFruit(Vector<SnakesProto.GameState.Coord> coords) {
        if (coords.size() == 0) return null;
        Random random = new Random();
        return coords.get(abs(random.nextInt()) % coords.size());
    }

    public void computeNextStep() {
        LinkedList<SnakesProto.GameState.Snake> snakes = new LinkedList<>();
        LinkedList<Integer> deadSnakes;
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            snake = computeSnake(snake);
            snakes.add(snake);
        }
        deadSnakes = checkDeath();
        snakes = killSome(deadSnakes, snakes);
        gameState = gameState.toBuilder().clearSnakes().build();
        for (SnakesProto.GameState.Snake snake : snakes) {
            gameState = gameState.toBuilder().addSnakes(snake).build();
        }
        addFood();
        NotifyAll(1);

    }

    public SnakesProto.GameState.Snake computeSnake(SnakesProto.GameState.Snake snake) {
        snake = snake.toBuilder().setHeadDirection(changeDirections.get(snake.getPlayerId())).build();
        snake = setPoints(snake, makeStep(snakeCoordinates.get(snake.getPlayerId()), snake.getHeadDirection()));
        snakeCoordinates.replace(snake.getPlayerId(), snakeToList(snake));
        return snake;
    }

    private LinkedList<Integer> checkDeath() {
        LinkedList<Integer> death = new LinkedList<>();
        for (int i = 0; i < gameState.getSnakesCount(); i++) {
            int id1 = gameState.getSnakes(i).getPlayerId();
            for (int j = 0; j < gameState.getSnakesCount(); j++) {
                int id2 = gameState.getSnakes(j).getPlayerId();
                int begin = 1;
                if (i != j) begin = 0;
                if (isDead(snakeCoordinates.get(id1).getFirst(), snakeCoordinates.get(id2), begin)) {
                    death.add(id1);
                }
            }
        }
        return death;
    }

    private LinkedList<SnakesProto.GameState.Snake> killSome(LinkedList<Integer> death, LinkedList<SnakesProto.GameState.Snake> s) {
        LinkedList<SnakesProto.GameState.Snake> snakes = new LinkedList<>();
        for (SnakesProto.GameState.Snake snake : s) {
            boolean isDead = false;
            for (Integer integer : death) {
                if (integer == snake.getPlayerId()) {
                    killSnake(integer);
                    isDead = true;
                    this.snakeCoordinates.remove(integer);
                    break;
                }
            }
            if (!isDead)
                snakes.add(snake);
        }
        return snakes;
    }

    private void killSnake(int id) {
        SnakesProto.GameState.Builder builder = gameState.toBuilder();
        for (SnakesProto.GameState.Coord coord : snakeCoordinates.get(id)) {
            Random random = new Random();
            int proc = abs(random.nextInt() % 100);
            if (proc < gameState.getConfig().getDeadFoodProb() * 100) {
                builder.addFoods(coord);
            }
        }
        gameState = builder.build();

        changeRole(id, SnakesProto.NodeRole.VIEWER, SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
    }

    private boolean isDead(SnakesProto.GameState.Coord head, LinkedList<SnakesProto.GameState.Coord> s2, int begin) {
        for (int i = begin; i < s2.size(); i++) {
            if (s2.get(i).equals(head))
                return true;
        }
        return false;
    }

    public LinkedList<SnakesProto.GameState.Coord> snakeToList(SnakesProto.GameState.Snake snake) {
        int i = 0;
        LinkedList<SnakesProto.GameState.Coord> list = new LinkedList<>();
        list.add(snake.getPoints(0));
        for (SnakesProto.GameState.Coord coord : snake.getPointsList()) {
            if (i == 0) {
                i++;
                continue;
            }

            if (coord.getY() != 0) {
                int y = coord.getY();
                int it = 1;
                if (y < 0) it = -1;
                while (y != 0) {
                    list.add(makeCoord(list.getLast().getX(), list.getLast().getY() + it));
                    y -= it;
                }
            }

            if (coord.getX() != 0) {
                int x = coord.getX();
                int it = 1;
                if (x < 0) it = -1;
                while (x != 0) {
                    list.add(makeCoord(list.getLast().getX() + it, list.getLast().getY()));
                    x -= it;
                }
            }
        }
        return list;
    }

    private LinkedList<SnakesProto.GameState.Coord> makeStep(LinkedList<SnakesProto.GameState.Coord> coords, SnakesProto.Direction direction) {
        switch (direction) {
            case UP -> coords.addFirst(makeCoord(coords.getFirst().getX(), coords.getFirst().getY() - 1));
            case DOWN -> coords.addFirst(makeCoord(coords.getFirst().getX(), coords.getFirst().getY() + 1));
            case LEFT -> coords.addFirst(makeCoord(coords.getFirst().getX() - 1, coords.getFirst().getY()));
            case RIGHT -> coords.addFirst(makeCoord(coords.getFirst().getX() + 1, coords.getFirst().getY()));
        }

        if (!isFood(coords.getFirst())) {
            coords.removeLast();
        } else removeFood(coords.getFirst());
        return coords;
    }

    private void removeFood(SnakesProto.GameState.Coord coord) {
        Vector<SnakesProto.GameState.Coord> vector = new Vector<>();
        for (SnakesProto.GameState.Coord coord1 : gameState.getFoodsList()) {
            if (!coord.equals(coord1)) vector.add(coord1);
        }
        SnakesProto.GameState.Builder builder = gameState.toBuilder().clearFoods();
        for (SnakesProto.GameState.Coord coord1 : vector) {
            builder.addFoods(coord1);
        }
        gameState = builder.build();
    }

    private boolean isFood(SnakesProto.GameState.Coord c) {
        for (SnakesProto.GameState.Coord coord : gameState.getFoodsList()) {
            if (c.equals(coord)) return true;
        }
        return false;
    }

    private SnakesProto.GameState.Snake setPoints(SnakesProto.GameState.Snake snake, LinkedList<SnakesProto.GameState.Coord> coords) {
        SnakesProto.GameState.Snake.Builder builder = snake.toBuilder().clearPoints();
        LinkedList<SnakesProto.GameState.Coord> list = new LinkedList<>();
        list.add(coords.getFirst());
        builder.addPoints(list.getLast());
        int prevDIffX = coords.get(1).getX() - coords.get(0).getX();
        int prevDiffY = coords.get(1).getY() - coords.get(0).getY();

        prevDIffX = normalizeDiff(prevDIffX);
        prevDiffY = normalizeDiff(prevDiffY);

        for (int i = 2; i < coords.size(); i++) {
            int diffX = coords.get(i).getX() - coords.get(i - 1).getX();
            int diffY = coords.get(i).getY() - coords.get(i - 1).getY();

            diffX = normalizeDiff(diffX);
            diffY = normalizeDiff(diffY);

            if ((diffX == 0 && prevDIffX != 0) || (diffY == 0 && prevDiffY != 0)) {
                list.add(makeNativeCoord(prevDIffX, prevDiffY));
                builder.addPoints(list.getLast());
                prevDIffX = diffX;
                prevDiffY = diffY;
            } else {
                prevDIffX += diffX;
                prevDiffY += diffY;
            }
        }

        builder.addPoints(makeNativeCoord(prevDIffX, prevDiffY));
        return builder.build();
    }

    private int normalizeDiff(int diff) {
        if (diff * (-1) == getHeight() - 1) return 1;
        if (diff == getHeight() - 1) return -1;
        return Integer.compare(diff, 0);
    }

    private SnakesProto.GameState.Coord makeCoord(int x, int y) {
        x = (x + getWidth()) % getWidth();
        y = (y + getHeight()) % getHeight();
        return SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y).build();
    }

    private SnakesProto.GameState.Coord makeNativeCoord(int x, int y) {
        return SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y).build();
    }

    public SnakesProto.Direction reversed(SnakesProto.Direction direction) {
        return switch (direction) {
            case UP -> SnakesProto.Direction.DOWN;
            case DOWN -> SnakesProto.Direction.UP;
            case LEFT -> SnakesProto.Direction.RIGHT;
            case RIGHT -> SnakesProto.Direction.LEFT;
        };
    }

    public void changeDirection(SnakesProto.Direction direction, int id, long num) {
        SnakesProto.GameState.Snake s = null;
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == id && snake.getState() != SnakesProto.GameState.Snake.SnakeState.ZOMBIE) {
                s = snake;
                break;
            }
        }

        if (s == null) return;
        if (s.getHeadDirection() != reversed(direction)) {
            if (num > changeHelper.get(id)) {
                changeHelper.put(id, num);
                changeDirections.put(s.getPlayerId(), direction);
            }
        }
    }

    public void updatePlayer(int id) {
        playerActivitiesTimestamps.put(id, Instant.now());
    }

    public void repair(int id) {
        sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
        snakeCoordinates = new HashMap<>();
        changeDirections = new HashMap<>();
        changeHelper = new HashMap<>();
        playerActivitiesTimestamps = new HashMap<>();

        sessionGamePlayers = gameState.getPlayers();
        changeRole(id, SnakesProto.NodeRole.MASTER, SnakesProto.GameState.Snake.SnakeState.ALIVE);

        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            snakeCoordinates.put(snake.getPlayerId(), snakeToList(snake));
        }
        for (SnakesProto.GamePlayer player : gameState.getPlayers().getPlayersList()) {
            changeHelper.put(player.getId(), 0L);
            playerActivitiesTimestamps.put(player.getId(), Instant.now());
        }
    }

    public void changeRole(int id, SnakesProto.NodeRole role, SnakesProto.GameState.Snake.SnakeState snakeState) {
        SnakesProto.GamePlayers.Builder players = SnakesProto.GamePlayers.newBuilder();
        for (SnakesProto.GamePlayer gamePlayer : gameState.getPlayers().getPlayersList()) {
            if (gamePlayer.getId() == id && role != null) gamePlayer = gamePlayer.toBuilder().setRole(role).build();
            players.addPlayers(gamePlayer);
        }
        LinkedList<SnakesProto.GameState.Snake> snakes = new LinkedList<>();
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == id && snakeState != null) snake = snake.toBuilder().setState(snakeState).build();
            snakes.add(snake);
        }
        SnakesProto.GameState.Builder builder = gameState.toBuilder();
        builder.clearSnakes();
        for (SnakesProto.GameState.Snake snake : snakes) {
            builder.addSnakes(snake);
        }
        gameState = builder.build();
        gameState = gameState.toBuilder().setPlayers(players).build();
    }

    public SnakesProto.GamePlayer getPlayer(int id) {
        for (SnakesProto.GamePlayer player : getGameState().getPlayers().getPlayersList()) {
            if (player.getId() == id) return player;
        }
        return null;
    }

    public boolean isAlive(int id) {
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == id && snake.getState() == SnakesProto.GameState.Snake.SnakeState.ALIVE)
                return true;
        }
        return false;
    }

    public static SnakesProto.GamePlayer getMaster(SnakesProto.GamePlayers players) {
        for (SnakesProto.GamePlayer player : players.getPlayersList()) {
            if (player.getRole() == SnakesProto.NodeRole.MASTER) return player;
        }
        return null;
    }

    public static SnakesProto.GamePlayer getDeputy(SnakesProto.GamePlayers players) {
        for (SnakesProto.GamePlayer player : players.getPlayersList()) {
            if (player.getRole() == SnakesProto.NodeRole.DEPUTY) return player;
        }
        return null;
    }

    public static SnakesProto.GamePlayer makePlayer(int id, String name, int port, String address, SnakesProto.NodeRole role) {
        return SnakesProto.GamePlayer.newBuilder()
                .setId(id)
                .setName(name)
                .setPort(port)
                .setRole(role)
                .setIpAddress(address)
                .setScore(0)
                .build();
    }
}
