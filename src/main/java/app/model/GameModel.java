package app.model;

import app.exceptions.ImpossibleOperationException;
import app.utilities.notifications.Publisher;
import proto.SnakesProto;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.abs;

public class GameModel extends Publisher {
    private static final int INITIAL_GAME_FIELD_SIZE = 20;
    private static final int INITIAL_STATE_ORDER = 0;
    private static final long ZERO_DIRECTION_CHANGES = 0L;
    private static final int SNAKE_HEAD_INDEX = 0;

    private ConcurrentHashMap<Integer, Long> directionChangesNumbersByPlayer = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Instant> activitiesTimestampsByPlayer = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, LinkedList<SnakesProto.GameState.Coord>> snakesAllCoordinatesByPlayer = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, SnakesProto.Direction> snakesDirectionsByPlayer = new ConcurrentHashMap<>();
    private SnakesProto.GamePlayers sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
    private SnakesProto.GameState gameState;
    private int sessionMasterId;

    public GameModel() {
        this.changeGameStateBy(this.getDefaultGameConfig());
    }

    private SnakesProto.GameConfig getDefaultGameConfig() {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(INITIAL_GAME_FIELD_SIZE)
                .setHeight(INITIAL_GAME_FIELD_SIZE)
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

    public void setSessionGamePlayers(SnakesProto.GamePlayers sessionGamePlayers) {
        this.sessionGamePlayers = sessionGamePlayers;
    }

    public ConcurrentHashMap<Integer, Long> getDirectionChangesNumbersByPlayer() {
        return directionChangesNumbersByPlayer;
    }

    public ConcurrentHashMap<Integer, Instant> getActivitiesTimestampsByPlayer() {
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

    public ConcurrentHashMap<Integer, LinkedList<SnakesProto.GameState.Coord>> getSnakesAllCoordinatesByPlayer() {
        return snakesAllCoordinatesByPlayer;
    }

    public int getSessionMasterId() {
        return sessionMasterId;
    }

    public void launchNewGameAsMaster(SnakesProto.GameConfig gameConfig, String playerName,
                                      int playerId, int playerPort) {
        this.sessionGamePlayers = SnakesProto.GamePlayers.newBuilder().build();
        this.snakesAllCoordinatesByPlayer = new ConcurrentHashMap<>();
        this.snakesDirectionsByPlayer = new ConcurrentHashMap<>();
        this.directionChangesNumbersByPlayer = new ConcurrentHashMap<>();
        this.activitiesTimestampsByPlayer = new ConcurrentHashMap<>();
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
        for (var snake : gameState.getSnakesList()) {
            snakesAllCoordinatesByPlayer.put(snake.getPlayerId(), getSnakeAllCoordinates(snake));
        }
        this.informAllSubscribers();
    }

    public void addNewPlayerToModel(SnakesProto.GamePlayer newPlayer) {
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
        var headCoordinates = convertToFieldCoordinate(randomX + 2, randomY + 2);
        var offsetFromHead = convertToFieldCoordinate(0, 1);
        return new LinkedList<>(List.of(headCoordinates, offsetFromHead));
    }

    private boolean isFieldRectangleFree(int leftCornerX, int rightCornerX,
                                         int leftCornerY, int rightCornerY) {
        for (int currentX = leftCornerX; currentX < rightCornerX; currentX++) {
            for (int currentY = leftCornerY; currentY < rightCornerY; currentY++) {
                for (var snake : gameState.getSnakesList()) {
                    for (var snakeCoordinates : getSnakeAllCoordinates(snake)) {
                        if (convertToFieldCoordinate(currentX, currentY).equals(snakeCoordinates)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    public void makeGameNextStep() {
        LinkedList<SnakesProto.GameState.Snake> aliveSnakes = new LinkedList<>();
        for (var snake : gameState.getSnakesList()) {
            snake = makeSnakeStep(snake);
            aliveSnakes.add(snake);
        }
        LinkedList<Integer> deadSnakeOwners = getDeadSnakeOwners();
        aliveSnakes = removeDeadOwnerSnakes(deadSnakeOwners, aliveSnakes);
        gameState = gameState.toBuilder().clearSnakes().build();
        for (var snake : aliveSnakes) {
            gameState = gameState.toBuilder().addSnakes(snake).build();
        }
        this.updateFieldFood();
        this.informAllSubscribers();
    }

    private void updateFieldFood() {
        ArrayList<SnakesProto.GameState.Coord> freeCoordinates = new ArrayList<>();
        for (int i = 0; i < getWidthFromGameConfig(); i++) {
            for (int j = 0; j < getHeightFromGameConfig(); j++) {
                var trackedCoordinate = convertToFieldCoordinate(i, j);
                boolean isTrackedCoordinateFree = true;
                for (var snake : gameState.getSnakesList()) {
                    for (var snakeCoordinate : getSnakeAllCoordinates(snake)) {
                        if (trackedCoordinate.equals(snakeCoordinate)) {
                            isTrackedCoordinateFree = false;
                            break;
                        }
                    }
                    if (!isTrackedCoordinateFree) {
                        break;
                    }
                }
                if (isTrackedCoordinateFree) {
                    for (SnakesProto.GameState.Coord foodCoordinate : gameState.getFoodsList()) {
                        if (trackedCoordinate.equals(foodCoordinate)) {
                            isTrackedCoordinateFree = false;
                            break;
                        }
                    }
                }
                if (isTrackedCoordinateFree) {
                    freeCoordinates.add(trackedCoordinate);
                }
            }
        }
        var gameStateBuilder = gameState.toBuilder();
        int totalFoodAmount = gameState.getFoodsCount();
        while (totalFoodAmount < gameState.getConfig().getFoodStatic() +
                gameState.getConfig().getFoodPerPlayer() * getAliveSnakesNumber()) {
            try {
                SnakesProto.GameState.Coord foodCoordinate = getRandomFreeCoordinate(freeCoordinates);
                gameStateBuilder.addFoods(foodCoordinate);
                freeCoordinates.remove(foodCoordinate);
                totalFoodAmount++;
            } catch (ImpossibleOperationException ignored) {
                break;
            }
        }
        gameState = gameStateBuilder.build();
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

    public SnakesProto.GameState.Coord getRandomFreeCoordinate(
            ArrayList<SnakesProto.GameState.Coord> freeCoordinates) throws ImpossibleOperationException {
        if (freeCoordinates.isEmpty()) {
            throw new ImpossibleOperationException();
        }
        return freeCoordinates.get(new Random().nextInt(freeCoordinates.size()));
    }

    public SnakesProto.GameState.Snake makeSnakeStep(SnakesProto.GameState.Snake snake) {
        var snakeNextDirection = snakesDirectionsByPlayer.get(snake.getPlayerId());
        if (snakeNextDirection == null) {
            System.err.println("AAAAA");
        }
        snake = snake.toBuilder().setHeadDirection(snakeNextDirection).build();
        var snakeCoordinatesAfterStep = changeSnakeCoordinatesAccordingToStep(
                snakesAllCoordinatesByPlayer.get(snake.getPlayerId()), snake.getHeadDirection());
        snake = this.getSnakeWithUpdatedKeyCoordinates(snake, snakeCoordinatesAfterStep);
        snakesAllCoordinatesByPlayer.replace(snake.getPlayerId(), getSnakeAllCoordinates(snake));
        return snake;
    }

    private LinkedList<Integer> getDeadSnakeOwners() {
        LinkedList<Integer> deadSnakeOwners = new LinkedList<>();
        for (int i = 0; i < gameState.getSnakesCount(); i++) {
            int firstSnakeOwner = gameState.getSnakes(i).getPlayerId();
            var firstSnakeHead = snakesAllCoordinatesByPlayer.get(firstSnakeOwner).getFirst();
            for (int j = 0; j < gameState.getSnakesCount(); j++) {
                int secondSnakeOwner = gameState.getSnakes(j).getPlayerId();
                int startCheckingIndex = (i != j) ? 0 : 1;
                if (isHeadCollidedWithBody(firstSnakeHead, snakesAllCoordinatesByPlayer.get(secondSnakeOwner), startCheckingIndex)) {
                    deadSnakeOwners.add(firstSnakeOwner);
                }
            }
        }
        return deadSnakeOwners;
    }

    private LinkedList<SnakesProto.GameState.Snake> removeDeadOwnerSnakes(LinkedList<Integer> deadSnakeOwners,
                                                                          LinkedList<SnakesProto.GameState.Snake> snakes) {
        LinkedList<SnakesProto.GameState.Snake> aliveSnakes = new LinkedList<>();
        for (var snake : snakes) {
            boolean isSnakeDead = false;
            for (var ownerId : deadSnakeOwners) {
                if (ownerId == snake.getPlayerId()) {
                    isSnakeDead = true;
                    this.generateFoodFromDeadSnake(ownerId);
                    snakesAllCoordinatesByPlayer.remove(ownerId);
                    break;
                }
            }
            if (!isSnakeDead) {
                aliveSnakes.add(snake);
            }
        }
        return aliveSnakes;
    }

    private void generateFoodFromDeadSnake(int deadSnakeOwnerId) {
        var gameStateBuilder = gameState.toBuilder();
        for (var snakeCoordinate : snakesAllCoordinatesByPlayer.get(deadSnakeOwnerId)) {
            if (isHeadCoordinate(snakeCoordinate, snakesAllCoordinatesByPlayer.get(deadSnakeOwnerId))) {
                continue;
            }
            int randomPoint = new Random().nextInt(100);
            if (randomPoint < gameState.getConfig().getDeadFoodProb() * 100) {
                gameStateBuilder.addFoods(snakeCoordinate);
            }
        }
        gameState = gameStateBuilder.build();
        this.changePlayerGameStatus(deadSnakeOwnerId, SnakesProto.NodeRole.VIEWER, SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
    }

    private boolean isHeadCollidedWithBody(SnakesProto.GameState.Coord headCoordinate,
                                           LinkedList<SnakesProto.GameState.Coord> bodyCoordinates,
                                           int startCheckingIndex) {
        for (int i = startCheckingIndex; i < bodyCoordinates.size(); i++) {
            if (bodyCoordinates.get(i).equals(headCoordinate))
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
                    keyCoordinates.add(convertToFieldCoordinate(
                            keyCoordinates.getLast().getX(),
                            keyCoordinates.getLast().getY() + singleOffset));
                    y -= singleOffset;
                }
            }

            if (isXOffsetExist(coordinate)) {
                int x = coordinate.getX();
                int singleOffset = (x < 0) ? -1 : 1;
                while (x != 0) {
                    keyCoordinates.add(convertToFieldCoordinate(
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

    private LinkedList<SnakesProto.GameState.Coord> changeSnakeCoordinatesAccordingToStep(
            LinkedList<SnakesProto.GameState.Coord> snakeCoordinates, SnakesProto.Direction chosenDirection) {
        int nextX = snakeCoordinates.getFirst().getX();
        int nextY = snakeCoordinates.getFirst().getY();
        switch (chosenDirection) {
            case UP -> nextY--;
            case DOWN -> nextY++;
            case LEFT -> nextX--;
            case RIGHT -> nextX++;
        }
        snakeCoordinates.addFirst(convertToFieldCoordinate(nextX, nextY));
        if (isThereFoodByCoordinate(snakeCoordinates.getFirst())) {
            removeFoodFromCoordinate(snakeCoordinates.getFirst());
        } else {
            snakeCoordinates.removeLast();
        }
        return snakeCoordinates;
    }

    private void removeFoodFromCoordinate(SnakesProto.GameState.Coord coordinate) {
        LinkedList<SnakesProto.GameState.Coord> leftoverFood = new LinkedList<>();
        for (var foodCoordinate : gameState.getFoodsList()) {
            if (!coordinate.equals(foodCoordinate)) {
                leftoverFood.add(foodCoordinate);
            }
        }
        var gameStateBuilder = gameState.toBuilder();
        gameStateBuilder.clearFoods();
        for (var foodCoordinate : leftoverFood) {
            gameStateBuilder.addFoods(foodCoordinate);
        }
        gameState = gameStateBuilder.build();
    }

    private boolean isThereFoodByCoordinate(SnakesProto.GameState.Coord coordinate) {
        for (var foodCoordinate : gameState.getFoodsList()) {
            if (coordinate.equals(foodCoordinate)) {
                return true;
            }
        }
        return false;
    }

    // TODO: баг с проходом через правую сторону на левую (обрезается змейка или падает программа)
    private SnakesProto.GameState.Snake getSnakeWithUpdatedKeyCoordinates(
            SnakesProto.GameState.Snake snake, LinkedList<SnakesProto.GameState.Coord> snakeCoordinatesAfterStep) {
        var snakeBuilder = snake.toBuilder();
        snakeBuilder.clearPoints();
        LinkedList<SnakesProto.GameState.Coord> keyCoordinates = new LinkedList<>();
        keyCoordinates.add(snakeCoordinatesAfterStep.getFirst());
        snakeBuilder.addPoints(keyCoordinates.getFirst());

        int differenceByXWithPrevious = transformXDifferenceAccordingToTorus(
                snakeCoordinatesAfterStep.get(1).getX() - snakeCoordinatesAfterStep.getFirst().getX());
        int differenceByYWithPrevious = transformYDifferenceAccordingToTorus(
                snakeCoordinatesAfterStep.get(1).getY() - snakeCoordinatesAfterStep.getFirst().getY());

        for (int i = 2; i < snakeCoordinatesAfterStep.size(); i++) {
            int currentDifferenceByX = transformXDifferenceAccordingToTorus(
                    snakeCoordinatesAfterStep.get(i).getX() - snakeCoordinatesAfterStep.get(i - 1).getX());
            int currentDifferenceByY = transformYDifferenceAccordingToTorus(
                    snakeCoordinatesAfterStep.get(i).getY() - snakeCoordinatesAfterStep.get(i - 1).getY());

            if ((currentDifferenceByX == 0 && differenceByXWithPrevious != 0) ||
                    (currentDifferenceByY == 0 && differenceByYWithPrevious != 0)) {
                keyCoordinates.add(convertToCoordinate(differenceByXWithPrevious, differenceByYWithPrevious));
                snakeBuilder.addPoints(keyCoordinates.getLast());
                differenceByXWithPrevious = currentDifferenceByX;
                differenceByYWithPrevious = currentDifferenceByY;
            } else {
                differenceByXWithPrevious += currentDifferenceByX;
                differenceByYWithPrevious += currentDifferenceByY;
            }
        }
        snakeBuilder.addPoints(convertToCoordinate(differenceByXWithPrevious, differenceByYWithPrevious));
        return snakeBuilder.build();
    }

    private int transformYDifferenceAccordingToTorus(int difference) {
        if (-difference == getHeightFromGameConfig() - 1) {
            return 1;
        }
        if (difference == getHeightFromGameConfig() - 1) {
            return -1;
        }
        return Integer.compare(difference, 0);
    }

    private int transformXDifferenceAccordingToTorus(int difference) {
        if (-difference == getWidthFromGameConfig() - 1) {
            return 1;
        }
        if (difference == getHeightFromGameConfig() - 1) {
            return -1;
        }
        return Integer.compare(difference, 0);
    }

    private SnakesProto.GameState.Coord convertToFieldCoordinate(int x, int y) {
        return SnakesProto.GameState.Coord.newBuilder()
                .setX((x + getWidthFromGameConfig()) % getWidthFromGameConfig())
                .setY((y + getHeightFromGameConfig()) % getHeightFromGameConfig())
                .build();
    }

    private SnakesProto.GameState.Coord convertToCoordinate(int x, int y) {
        return SnakesProto.GameState.Coord.newBuilder().setX(x).setY(y).build();
    }

    public void changeSnakeDirectionById(SnakesProto.Direction chosenDirection, int playerId, long directionChangesNumber) {
        SnakesProto.GameState.Snake playerSnake = null;
        var zombieSnakeIndicator = SnakesProto.GameState.Snake.SnakeState.ZOMBIE;
        for (var snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == playerId && !snake.getState().equals(zombieSnakeIndicator)) {
                playerSnake = snake;
                break;
            }
        }

        if (playerSnake != null && !playerSnake.getHeadDirection().equals(getReverseDirectionTo(chosenDirection))) {
            if (directionChangesNumber > directionChangesNumbersByPlayer.get(playerId)) {
                directionChangesNumbersByPlayer.put(playerId, directionChangesNumber);
                snakesDirectionsByPlayer.put(playerSnake.getPlayerId(), chosenDirection);
            }
        }
    }

    private SnakesProto.Direction getReverseDirectionTo(SnakesProto.Direction direction) {
        return switch (direction) {
            case UP -> SnakesProto.Direction.DOWN;
            case DOWN -> SnakesProto.Direction.UP;
            case LEFT -> SnakesProto.Direction.RIGHT;
            case RIGHT -> SnakesProto.Direction.LEFT;
        };
    }

    public void makePlayerTimestamp(int playerId) {
        activitiesTimestampsByPlayer.put(playerId, Instant.now());
    }

    public void rebuiltGameModel(int playerId) {
        snakesAllCoordinatesByPlayer = new ConcurrentHashMap<>();
        snakesDirectionsByPlayer = new ConcurrentHashMap<>();
        directionChangesNumbersByPlayer = new ConcurrentHashMap<>();
        activitiesTimestampsByPlayer = new ConcurrentHashMap<>();
        this.changePlayerGameStatus(sessionMasterId, SnakesProto.NodeRole.VIEWER, SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
        this.changePlayerGameStatus(playerId, SnakesProto.NodeRole.MASTER, SnakesProto.GameState.Snake.SnakeState.ALIVE);

        this.sessionMasterId = playerId;

        sessionGamePlayers = gameState.getPlayers();
        for (var snake : gameState.getSnakesList()) {
            snakesAllCoordinatesByPlayer.put(snake.getPlayerId(), getSnakeAllCoordinates(snake));
            snakesDirectionsByPlayer.put(snake.getPlayerId(), snake.getHeadDirection());
        }
        for (var player : gameState.getPlayers().getPlayersList()) {
            directionChangesNumbersByPlayer.put(player.getId(), ZERO_DIRECTION_CHANGES);
            activitiesTimestampsByPlayer.put(player.getId(), Instant.now());
        }
        System.err.println("I AM HERE IN REBUILDING");
        this.informAllSubscribers();
    }

    public void changePlayerGameStatus(int playerId, SnakesProto.NodeRole playerRole,
                                       SnakesProto.GameState.Snake.SnakeState snakeState) {
        var overwritingPlayers = SnakesProto.GamePlayers.newBuilder();
        for (SnakesProto.GamePlayer existingPlayer : gameState.getPlayers().getPlayersList()) {
            if (existingPlayer.getId() == playerId && playerRole != null) {
                overwritingPlayers.addPlayers(existingPlayer.toBuilder().setRole(playerRole).build());
            } else {
                overwritingPlayers.addPlayers(existingPlayer);
            }
        }

        LinkedList<SnakesProto.GameState.Snake> overwritingSnakes = new LinkedList<>();
        for (var existingSnakes : gameState.getSnakesList()) {
            if (existingSnakes.getPlayerId() == playerId && snakeState != null) {
                overwritingSnakes.add(existingSnakes.toBuilder().setState(snakeState).build());
            } else {
                overwritingSnakes.add(existingSnakes);
            }
        }

        SnakesProto.GameState.Builder newGameStateBuilder = gameState.toBuilder();
        newGameStateBuilder.clearSnakes();
        newGameStateBuilder.clearPlayers();
        newGameStateBuilder.setPlayers(overwritingPlayers.build());
        for (var snake : overwritingSnakes) {
            newGameStateBuilder.addSnakes(snake);
        }
        this.gameState = newGameStateBuilder.build();
    }

    public SnakesProto.GamePlayer getPlayerById(int playerId) {
        for (var player : gameState.getPlayers().getPlayersList()) {
            if (player.getId() == playerId) {
                return player;
            }
        }
        return null;
    }

    public boolean isPlayerSnakeAlive(int playerId) {
        var aliveSnakeIndicator = SnakesProto.GameState.Snake.SnakeState.ALIVE;
        for (var snake : gameState.getSnakesList()) {
            if (snake.getPlayerId() == playerId && snake.getState().equals(aliveSnakeIndicator))
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "\nGameModel{" +
                "\ndirectionChangesNumbersByPlayer=" + directionChangesNumbersByPlayer +
                "\nactivitiesTimestampsByPlayer=" + activitiesTimestampsByPlayer +
                "\nsnakesAllCoordinatesByPlayer=" + snakesAllCoordinatesByPlayer +
                "\nsnakesDirectionsByPlayer=" + snakesDirectionsByPlayer +
                "\nsessionGamePlayers=" + sessionGamePlayers +
                "\ngameState=" + gameState +
                "\nsessionMasterId=" + sessionMasterId +
                "\n}";
    }
}
