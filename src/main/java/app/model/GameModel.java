package app.model;

import app.utilities.Publisher;
import proto.SnakesProto;

import java.time.Instant;
import java.util.*;

import static java.lang.Math.abs;

public class GameModel extends Publisher {
    private static final int INITIAL_STATE_ORDER = 0;
    private static final long ZERO_DIRECTION_CHANGES = 0L;
    private static final int SNAKE_HEAD_INDEX = 0;

    private HashMap<Integer, Long> directionChangesNumbersByPlayer = new HashMap<>();
    private HashMap<Integer, Instant> activitiesTimestampsByPlayer = new HashMap<>();
    private HashMap<Integer, LinkedList<SnakesProto.GameState.Coord>> snakesAllCoordinatesByPlayer = new HashMap<>();
    private HashMap<Integer, SnakesProto.Direction> snakesDirectionsByPlayer = new HashMap<>();
    private SnakesProto.GamePlayers sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
    private SnakesProto.GameState gameState;
    private int sessionMasterId;

    public GameModel() {
        this.changeGameStateBy(this.getDefaultGameConfig());
    }

    // TODO: если убрать sets, то новый игрок не может начать играть по кнопке Войти.
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
        this.gameState = SnakesProto.GameState.newBuilder()
                .setConfig(gameConfig)
                .setPlayers(this.sessionGamePlayers)
                .setStateOrder(INITIAL_STATE_ORDER)
                .build();
    }

    public int getWidthFromGameConfig() {
        return gameState.getConfig().getWidth();
    }

    public int getHeightFromGameConfig() {
        return gameState.getConfig().getHeight();
    }

    public SnakesProto.GamePlayers getSessionGamePlayers() {
        return sessionGamePlayers;
    }

    public HashMap<Integer, Long> getDirectionChangesNumbersByPlayer() {
        return directionChangesNumbersByPlayer;
    }

    public HashMap<Integer, Instant> getActivitiesTimestampsByPlayer() {
        return activitiesTimestampsByPlayer;
    }

    public SnakesProto.GameConfig getGameConfig() {
        return gameState.getConfig();
    }

    public void setSessionMasterId(int masterId) {
        sessionMasterId = masterId;
    }

    public SnakesProto.GameState getGameState() {
        return gameState;
    }

    public HashMap<Integer, LinkedList<SnakesProto.GameState.Coord>> getSnakesAllCoordinatesByPlayer() {
        return snakesAllCoordinatesByPlayer;
    }

    public int getSessionMasterId() {
        return sessionMasterId;
    }

    public void launchNewGameAsMaster(SnakesProto.GameConfig gameConfig, String playerName,
                                      int playerId, int playerPort) {
        this.sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
        this.snakesAllCoordinatesByPlayer = new HashMap<>();
        this.snakesDirectionsByPlayer = new HashMap<>();
        this.directionChangesNumbersByPlayer = new HashMap<>();
        this.activitiesTimestampsByPlayer = new HashMap<>();
        this.sessionMasterId = playerId;
        this.changeGameStateBy(gameConfig);
        SnakesProto.GamePlayer me = SnakesProto.GamePlayer.newBuilder()
                .setId(playerId)
                .setName(playerName)
                .setPort(playerPort)
                .setRole(SnakesProto.NodeRole.MASTER)
                .setIpAddress("")
                .setScore(0)
                .build();

        this.addNewPlayerToModel(me);
        this.informAllSubscribers();
    }

    public void setGameState(SnakesProto.GameState gameState) {
        this.gameState = gameState;
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            snakesAllCoordinatesByPlayer.put(snake.getPlayerId(), getSnakeAllCoordinates(snake));
        }
        informAllSubscribers();
    }

    // TODO: магические константы (проверить на соответствие return значений)
    public int addNewPlayerToModel(SnakesProto.GamePlayer newPlayer) {
        boolean isPlayerUnknown = true;
        for (SnakesProto.GamePlayer existingPlayer : gameState.getPlayers().getPlayersList()) {
            if (existingPlayer.getId() == newPlayer.getId()) {
                isPlayerUnknown = false;
                this.changePlayerGameStatus(newPlayer.getId(),
                        SnakesProto.NodeRole.NORMAL,
                        SnakesProto.GameState.Snake.SnakeState.ALIVE);
            }
        }
        SnakesProto.GameState.Snake snake = null;
        if (newPlayer.getRole() != SnakesProto.NodeRole.VIEWER) {
            snake = this.addSnakeIfPossible(newPlayer.getId());
            if (snake != null) {
                snakesAllCoordinatesByPlayer.put(newPlayer.getId(), getSnakeAllCoordinates(snake));
            }
        }
        if (snake == null) {
            newPlayer = newPlayer.toBuilder().setRole(SnakesProto.NodeRole.VIEWER).build();
        }
        if (isPlayerUnknown) {
            sessionGamePlayers = gameState.getPlayers().toBuilder().addPlayers(newPlayer).build();
            gameState = gameState.toBuilder().setPlayers(sessionGamePlayers).build();
        }
        return (snake == null) ? 2 : 0;
    }

    private SnakesProto.GameState.Snake addSnakeIfPossible(int playerId) {
        for (var snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == playerId) {
                return null;
            }
        }
        var freeCoordinatesList = this.getEmptyPlaceForSnake();
        if (freeCoordinatesList == null) {
            return null;
        }

        var snakeBuilder = SnakesProto.GameState.Snake.newBuilder();
        for (var coordinate : freeCoordinatesList) {
            snakeBuilder.addPoints(coordinate);
        }
        var direction = SnakesProto.Direction.DOWN;
        var builtSnake = snakeBuilder
                .setPlayerId(playerId)
                .setHeadDirection(direction)
                .setState(SnakesProto.GameState.Snake.SnakeState.ALIVE)
                .build();

        this.gameState = gameState.toBuilder().addSnakes(builtSnake).build();
        this.snakesDirectionsByPlayer.put(builtSnake.getPlayerId(), builtSnake.getHeadDirection());
        this.directionChangesNumbersByPlayer.put(builtSnake.getPlayerId(), ZERO_DIRECTION_CHANGES);
        return snakeBuilder.build();
    }

    // TODO: магические константы
    private LinkedList<SnakesProto.GameState.Coord> getEmptyPlaceForSnake() {
        final int yOffset = 4;
        Random numbersGenerator = new Random();
        boolean isThereFreeCoordinates = false;
        int randomX = numbersGenerator.nextInt(getWidthFromGameConfig());
        int randomY = numbersGenerator.nextInt(getHeightFromGameConfig());
        for (int i = randomX; i < randomX + getWidthFromGameConfig(); i++) {
            for (int j = randomY; j < randomY + getHeightFromGameConfig(); j++) {
                if (isFieldRectangleFree(i, j, i + yOffset, j + yOffset)) {
                    isThereFreeCoordinates = true;
                    break;
                }
            }
        }
        if (!isThereFreeCoordinates) {
            return null;
        }
        var headCoordinates = buildCoordinatesBy(randomX + 2, randomY + 2);
        var offsetFromHead = buildCoordinatesBy(0, 1);
        return new LinkedList<>(List.of(headCoordinates, offsetFromHead));
    }

    private boolean isFieldRectangleFree(int leftCornerX, int rightCornerX,
                                         int leftCornerY, int rightCornerY) {
        for (int currentX = leftCornerX; currentX < rightCornerX; currentX++) {
            for (int currentY = leftCornerY; currentY < rightCornerY; currentY++) {
                for (var snake : gameState.getSnakesList()) {
                    for (var snakeCoordinates : snake.getPointsList()) {
                        if (buildCoordinatesBy(currentX, currentY).equals(snakeCoordinates)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private int getAliveSnakesNumber() {
        int aliveSnakesNumber = 0;
        var aliveSnakeIndicator = SnakesProto.GameState.Snake.SnakeState.ALIVE;
        for (var snake : gameState.getSnakesList()) {
            if (snake.getState().equals(aliveSnakeIndicator)) {
                aliveSnakesNumber++;
            }
        }
        return aliveSnakesNumber;
    }

    public void computeNextStep() {
        LinkedList<SnakesProto.GameState.Snake> snakes = new LinkedList<>();
        LinkedList<Integer> playersWithDeadSnakesIds;
        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            snake = computeSnake(snake);
            snakes.add(snake);
        }
        playersWithDeadSnakesIds = checkDeath();
        snakes = killSome(playersWithDeadSnakesIds, snakes);
        gameState = gameState.toBuilder().clearSnakes().build();
        for (SnakesProto.GameState.Snake snake : snakes) {
            gameState = gameState.toBuilder().addSnakes(snake).build();
        }
        this.addFood();
        this.informAllSubscribers();

    }

    private void addFood() {
        Vector<SnakesProto.GameState.Coord> freePlaces = new Vector<>();
        for (int i = 0; i < getWidthFromGameConfig(); i++) {
            for (int j = 0; j < getHeightFromGameConfig(); j++) {
                SnakesProto.GameState.Coord coord1 = buildCoordinatesBy(i, j);
                for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
                    for (SnakesProto.GameState.Coord coord : snake.getPointsList()) {
                        if (!coord1.equals(coord)) {
                            freePlaces.add(buildCoordinatesBy(i, j));
                        }
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
        while (foodCount < gameState.getConfig().getFoodStatic() + gameState.getConfig().getFoodPerPlayer() * getAliveSnakesNumber()) {
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

    public SnakesProto.GameState.Snake computeSnake(SnakesProto.GameState.Snake snake) {
        snake = snake.toBuilder().setHeadDirection(snakesDirectionsByPlayer.get(snake.getPlayerId())).build();
        snake = setPoints(snake, makeStep(snakesAllCoordinatesByPlayer.get(snake.getPlayerId()), snake.getHeadDirection()));
        snakesAllCoordinatesByPlayer.replace(snake.getPlayerId(), getSnakeAllCoordinates(snake));
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
                if (isDead(snakesAllCoordinatesByPlayer.get(id1).getFirst(), snakesAllCoordinatesByPlayer.get(id2), begin)) {
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
                    this.snakesAllCoordinatesByPlayer.remove(integer);
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
        for (SnakesProto.GameState.Coord coord : snakesAllCoordinatesByPlayer.get(id)) {
            Random random = new Random();
            int proc = abs(random.nextInt() % 100);
            if (proc < gameState.getConfig().getDeadFoodProb() * 100) {
                builder.addFoods(coord);
            }
        }
        gameState = builder.build();

        changePlayerGameStatus(id, SnakesProto.NodeRole.VIEWER, SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
    }

    private boolean isDead(SnakesProto.GameState.Coord head, LinkedList<SnakesProto.GameState.Coord> s2, int begin) {
        for (int i = begin; i < s2.size(); i++) {
            if (s2.get(i).equals(head))
                return true;
        }
        return false;
    }

    public LinkedList<SnakesProto.GameState.Coord> getSnakeAllCoordinates(SnakesProto.GameState.Snake snake) {
        LinkedList<SnakesProto.GameState.Coord> keyCoordinates = new LinkedList<>();
        for (SnakesProto.GameState.Coord coordinate : snake.getPointsList()) {
            if (isHeadCoordinate(coordinate, snake.getPointsList())) {
                keyCoordinates.add(coordinate);
                continue;
            }

            if (isYOffsetExist(coordinate)) {
                int y = coordinate.getY();
                int singleOffset = (y < 0) ? -1 : 1;
                while (y != 0) {
                    keyCoordinates.add(buildCoordinatesBy(
                            keyCoordinates.getLast().getX(),
                            keyCoordinates.getLast().getY() + singleOffset));
                    y -= singleOffset;
                }
            }

            if (isXOffsetExist(coordinate)) {
                int x = coordinate.getX();
                int singleOffset = (x < 0) ? -1 : 1;
                while (x != 0) {
                    keyCoordinates.add(buildCoordinatesBy(
                            keyCoordinates.getLast().getX() + singleOffset,
                            keyCoordinates.getLast().getY()));
                    x -= singleOffset;
                }
            }
        }
        return keyCoordinates;
    }

    private boolean isHeadCoordinate(SnakesProto.GameState.Coord coordinate,
                                     List<SnakesProto.GameState.Coord> coordinatesList) {
        return coordinatesList.get(SNAKE_HEAD_INDEX).equals(coordinate);
    }

    private boolean isYOffsetExist(SnakesProto.GameState.Coord coordinate) {
        return 0 != coordinate.getY();
    }

    private boolean isXOffsetExist(SnakesProto.GameState.Coord coordinate) {
        return 0 != coordinate.getX();
    }

    private LinkedList<SnakesProto.GameState.Coord> makeStep
            (LinkedList<SnakesProto.GameState.Coord> coords, SnakesProto.Direction direction) {
        switch (direction) {
            case UP -> coords.addFirst(buildCoordinatesBy(coords.getFirst().getX(), coords.getFirst().getY() - 1));
            case DOWN -> coords.addFirst(buildCoordinatesBy(coords.getFirst().getX(), coords.getFirst().getY() + 1));
            case LEFT -> coords.addFirst(buildCoordinatesBy(coords.getFirst().getX() - 1, coords.getFirst().getY()));
            case RIGHT -> coords.addFirst(buildCoordinatesBy(coords.getFirst().getX() + 1, coords.getFirst().getY()));
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

    private SnakesProto.GameState.Snake setPoints(SnakesProto.GameState.Snake
                                                          snake, LinkedList<SnakesProto.GameState.Coord> coords) {
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
        if (diff * (-1) == getHeightFromGameConfig() - 1) return 1;
        if (diff == getHeightFromGameConfig() - 1) return -1;
        return Integer.compare(diff, 0);
    }

    private SnakesProto.GameState.Coord buildCoordinatesBy(int x, int y) {
        return SnakesProto.GameState.Coord.newBuilder()
                .setX((x + getWidthFromGameConfig()) % getWidthFromGameConfig())
                .setY((y + getHeightFromGameConfig()) % getHeightFromGameConfig())
                .build();
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
            if (num > directionChangesNumbersByPlayer.get(id)) {
                directionChangesNumbersByPlayer.put(id, num);
                snakesDirectionsByPlayer.put(s.getPlayerId(), direction);
            }
        }
    }

    public void updatePlayer(int id) {
        activitiesTimestampsByPlayer.put(id, Instant.now());
    }

    public void repair(int id) {
        snakesAllCoordinatesByPlayer = new HashMap<>();
        snakesDirectionsByPlayer = new HashMap<>();
        directionChangesNumbersByPlayer = new HashMap<>();
        activitiesTimestampsByPlayer = new HashMap<>();

        sessionGamePlayers = gameState.getPlayers();
        changePlayerGameStatus(id, SnakesProto.NodeRole.MASTER, SnakesProto.GameState.Snake.SnakeState.ALIVE);

        for (SnakesProto.GameState.Snake snake : gameState.getSnakesList()) {
            snakesAllCoordinatesByPlayer.put(snake.getPlayerId(), getSnakeAllCoordinates(snake));
        }
        for (SnakesProto.GamePlayer player : gameState.getPlayers().getPlayersList()) {
            directionChangesNumbersByPlayer.put(player.getId(), 0L);
            activitiesTimestampsByPlayer.put(player.getId(), Instant.now());
        }
    }

    public void changePlayerGameStatus(int playerId, SnakesProto.NodeRole playerRole,
                                       SnakesProto.GameState.Snake.SnakeState snakeState) {
        var rewritingPlayers = SnakesProto.GamePlayers.newBuilder();
        for (SnakesProto.GamePlayer existingPlayer : gameState.getPlayers().getPlayersList()) {
            if (existingPlayer.getId() == playerId && playerRole != null) {
                rewritingPlayers.addPlayers(existingPlayer.toBuilder().setRole(playerRole).build());
            } else {
                rewritingPlayers.addPlayers(existingPlayer);
            }
        }

        LinkedList<SnakesProto.GameState.Snake> rewritingSnakes = new LinkedList<>();
        for (var existingSnakes : gameState.getSnakesList()) {
            if (existingSnakes.getPlayerId() == playerId && snakeState != null) {
                rewritingSnakes.add(existingSnakes.toBuilder().setState(snakeState).build());
            } else {
                rewritingSnakes.add(existingSnakes);
            }
        }

        SnakesProto.GameState.Builder newGameStateBuilder = gameState.toBuilder();
        newGameStateBuilder.clearSnakes();
        for (var snake : rewritingSnakes) {
            newGameStateBuilder.addSnakes(snake);
        }
        this.gameState = newGameStateBuilder.build();
        this.gameState = gameState.toBuilder().setPlayers(rewritingPlayers).build();
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

    public static SnakesProto.GamePlayer makePlayer(
            int playerId, String playerName, int inetPort, String
            inetAddress, SnakesProto.NodeRole playerRole) {
        return SnakesProto.GamePlayer.newBuilder()
                .setId(playerId)
                .setName(playerName)
                .setPort(inetPort)
                .setRole(playerRole)
                .setIpAddress(inetAddress)
                .setScore(0)
                .build();
    }
}
