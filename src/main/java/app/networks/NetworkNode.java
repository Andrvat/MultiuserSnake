package app.networks;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.model.GameModel;
import app.utilities.DebugPrinter;
import app.utilities.GamePlayersMaker;
import app.view.ViewController;
import app.utilities.notifications.Subscriber;
import lombok.Builder;
import proto.SnakesProto;

public class NetworkNode extends Subscriber {
    private static final String MULTICAST_IP = "239.192.0.4";
    private static final int MULTICAST_PORT = 9192;
    private static final int SOCKETS_TIMEOUT_IN_MILLIS = 3000;
    private static final int ANNOUNCEMENT_MESSAGE_PERIOD_IN_MILLIS = 3000;

    private final String nodeName;
    private SnakesProto.NodeRole nodeRole;
    private final UUID nodeId;

    private InetAddress senderInetAddress;
    private int senderPort;

    private final InetAddress myInetAddress;
    private final int myPort;

    private final MulticastSocket multicastSocket;
    private final DatagramSocket datagramSocket;
    private DatagramPacket sendingDatagramPacket;

    private final GameModel gameModel;
    private final ViewController viewController;

    private SnakesProto.GamePlayer masterPlayer;
    private SnakesProto.GamePlayer deputyPlayer = null;

    private static int gameStateNumber = 0;

    private long lastAnnouncementTimestamp;
    private long lastStateTimestamp;
    private long lastSentMessageTimestamp;

    private static final SnakesProto.NodeRole VIEWER_ROLE = SnakesProto.NodeRole.VIEWER;
    private static final SnakesProto.NodeRole MASTER_ROLE = SnakesProto.NodeRole.MASTER;
    private static final SnakesProto.NodeRole DEPUTY_ROLE = SnakesProto.NodeRole.DEPUTY;
    private static final SnakesProto.NodeRole NORMAL_ROLE = SnakesProto.NodeRole.NORMAL;

    private final ConcurrentHashMap<CommunicationMessage, Instant> announcementsTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CommunicationMessage, Instant> requiredSendingMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CommunicationMessage, Instant> requiredConfirmationMessages = new ConcurrentHashMap<>();

    @Builder
    public NetworkNode(GameModel gameModel, SnakesProto.NodeRole nodeRole, String nodeName,
                       InetAddress myInetAddress, int myPort, UUID nodeId) throws Exception {
        super(gameModel);
        this.gameModel = gameModel;
        this.nodeRole = nodeRole;
        this.nodeName = nodeName;
        this.myInetAddress = myInetAddress;
        this.myPort = myPort;
        this.nodeId = nodeId;

        viewController = ViewController.builder()
                .networkNode(this)
                .gameModel(gameModel)
                .build();
        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        datagramSocket = new DatagramSocket(myPort, myInetAddress);
        connectMulticastSocketToGroup();
        setTimeoutsForSockets();
    }

    private void connectMulticastSocketToGroup() throws IOException {
        multicastSocket.joinGroup(InetAddress.getByName(MULTICAST_IP));
    }

    private void setTimeoutsForSockets() throws SocketException {
        multicastSocket.setSoTimeout(SOCKETS_TIMEOUT_IN_MILLIS);
        datagramSocket.setSoTimeout(SOCKETS_TIMEOUT_IN_MILLIS);
    }

    public void startCommunicating() {
        UnicastReceiver unicastReceiver = UnicastReceiver.builder()
                .datagramSocket(datagramSocket)
                .networkNode(this)
                .build();
        MulticastReceiver multicastReceiver = MulticastReceiver.builder()
                .multicastSocket(multicastSocket)
                .networkNode(this)
                .build();

        lastAnnouncementTimestamp = getEpochMillisBySystemClockInstant();
        lastStateTimestamp = getEpochMillisBySystemClockInstant();
        lastSentMessageTimestamp = getEpochMillisBySystemClockInstant();

        unicastReceiver.start();
        multicastReceiver.start();

        while (true) {
            communicate();
        }
    }

    private long getEpochMillisBySystemClockInstant() {
        return Instant.now().toEpochMilli();
    }

    private void communicate() {
        processGameStep();
        sendAllRemainingMessages();
        if (moreTimeHasPassedThanPeriod(lastAnnouncementTimestamp, ANNOUNCEMENT_MESSAGE_PERIOD_IN_MILLIS)) {
            if (nodeRole.equals(MASTER_ROLE)) {
                sendAnnouncementsToMulticastGroup();
                lastAnnouncementTimestamp = Instant.now().toEpochMilli();
            }
        }
    }

    private void processGameStep() {
        boolean hasTimePassed = makeNextStepIfTimePassed();
        for (var requiredSendingMessage : requiredSendingMessages.entrySet()) {
            try {
                CommunicationMessage correspondingMessage = requiredSendingMessage.getKey();
                if (correspondingMessage.getReceiverPlayer() == null) {
                    if (masterPlayer != null)
                        correspondingMessage.setReceiverPlayer(masterPlayer);
                    else {
                        requiredSendingMessages.remove(correspondingMessage);
                        continue;
                    }
                }
                if (correspondingMessage.getMessage() == null) {
                    correspondingMessage.setMessage(SnakesProto.GameMessage.newBuilder()
                            .setMsgSeq(incrementStateNumber())
                            .setSenderId(nodeId.hashCode())
                            .setState(SnakesProto.GameMessage.StateMsg.newBuilder()
                                    .setState(gameModel.getGameState()).build())
                            .build());
                }
                var messageBytes = correspondingMessage.getMessage().toByteArray();
                sendingDatagramPacket = new DatagramPacket(
                        messageBytes,
                        messageBytes.length,
                        InetAddress.getByName(correspondingMessage.getReceiverPlayer().getIpAddress()),
                        correspondingMessage.getReceiverPlayer().getPort());
                if (correspondingMessage.getMessage().getTypeCase()
                        .equals(SnakesProto.GameMessage.TypeCase.STATE) && hasTimePassed) {
                    datagramSocket.send(sendingDatagramPacket);
                } else {
                    datagramSocket.send(sendingDatagramPacket);
                    if (!correspondingMessage.getMessage().getTypeCase().equals(SnakesProto.GameMessage.TypeCase.ACK)) {
                        requiredConfirmationMessages.put(correspondingMessage, Instant.now());
                    }
                    requiredSendingMessages.remove(correspondingMessage);
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        if (hasTimePassed) {
            System.gc();
        }
    }

    private void sendAllRemainingMessages() {
        if (!moreTimeHasPassedThanPeriod(lastSentMessageTimestamp,
                gameModel.getGameState().getConfig().getPingDelayMs())) {
            return;
        }
        processPlayersActivitiesByPings();
        for (var requiredSendingMessage : requiredSendingMessages.entrySet()) {
            try {
                CommunicationMessage correspondingMessage = requiredSendingMessage.getKey();
                if (correspondingMessage.getReceiverPlayer() == null) {
                    if (masterPlayer != null) {
                        correspondingMessage.setReceiverPlayer(masterPlayer);
                    } else {
                        requiredSendingMessages.remove(correspondingMessage);
                        continue;
                    }
                }
                var currentStateMessage = SnakesProto.GameMessage.StateMsg.newBuilder()
                        .setState(gameModel.getGameState());
                var updatedByCurrentStateMessage = correspondingMessage.getMessage().toBuilder()
                        .setState(currentStateMessage).build();
                correspondingMessage.setMessage(updatedByCurrentStateMessage);

                var messageBytes = correspondingMessage.getMessage().toByteArray();
                sendingDatagramPacket = new DatagramPacket(
                        messageBytes,
                        messageBytes.length,
                        InetAddress.getByName(correspondingMessage.getReceiverPlayer().getIpAddress()),
                        correspondingMessage.getReceiverPlayer().getPort());
                datagramSocket.send(sendingDatagramPacket);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void processPlayersActivitiesByPings() {
        for (var activityTimestamp : gameModel.getActivitiesTimestampsByPlayer().entrySet()) {
            if (Instant.now().toEpochMilli() - activityTimestamp.getValue().toEpochMilli() >
                    gameModel.getGameState().getConfig().getNodeTimeoutMs()) {
                if (gameModel.getPlayerById(activityTimestamp.getKey()).getRole().equals(MASTER_ROLE)) {
                    if (nodeRole.equals(DEPUTY_ROLE)) {
                        nodeRole = MASTER_ROLE;
                        gameModel.rebuiltGameModel(nodeId.hashCode());
                        for (var player : gameModel.getGameState().getPlayers().getPlayersList()) {
                            this.sendRoleChangeMessage(player, MASTER_ROLE, NORMAL_ROLE);
                        }
                    } else if (nodeRole.equals(NORMAL_ROLE)) {
                        masterPlayer = GamePlayersMaker.getDeputyPlayerFromList(gameModel.getGameState().getPlayers());
                    }
                }
                this.sendPingMessage(gameModel.getPlayerById(activityTimestamp.getKey()));
            }
        }
        lastSentMessageTimestamp = Instant.now().toEpochMilli();
        System.gc();
    }

    private boolean makeNextStepIfTimePassed() {
        boolean hasTimePassed = false;
        int stateDelay = gameModel.getGameState().getConfig().getStateDelayMs();
        if (moreTimeHasPassedThanPeriod(lastStateTimestamp, stateDelay) && nodeRole.equals(MASTER_ROLE)) {
            gameModel.makeGameNextStep();
            deputyPlayer = GamePlayersMaker.getDeputyPlayerFromList(gameModel.getGameState().getPlayers());
            for (var player : gameModel.getGameState().getPlayers().getPlayersList()) {
                if (nodeId.hashCode() != player.getId()) {
                    requiredSendingMessages.put(
                            CommunicationMessage.builder()
                                    .message(null)
                                    .senderPlayer(masterPlayer)
                                    .receiverPlayer(player)
                                    .build(),
                            Instant.now());
                }
            }
            hasTimePassed = true;
            lastStateTimestamp = getEpochMillisBySystemClockInstant();
        }
        return hasTimePassed;
    }

    private boolean moreTimeHasPassedThanPeriod(long timeLabel, long period) {
        return Instant.now().toEpochMilli() - timeLabel > period;
    }

    private void sendAnnouncementsToMulticastGroup() {
        var announcementMessage = SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                .setCanJoin(true)
                .setPlayers(gameModel.getSessionGamePlayers())
                .setConfig(gameModel.getGameConfig())
                .build();
        int unusedInformation = 0;
        var gameMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(unusedInformation)
                .setSenderId(unusedInformation)
                .setReceiverId(unusedInformation)
                .setAnnouncement(announcementMessage)
                .build();
        try {
            var messageBytes = gameMessage.toByteArray();
            sendingDatagramPacket = new DatagramPacket(messageBytes,
                    messageBytes.length,
                    InetAddress.getByName(MULTICAST_IP),
                    MULTICAST_PORT);
            multicastSocket.send(sendingDatagramPacket);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void sendPingMessage(SnakesProto.GamePlayer receiverPlayer) {
        var gameMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .setSenderId(nodeId.hashCode())
                .setReceiverId(receiverPlayer.getId())
                .build();
        CommunicationMessage communicationMessage = CommunicationMessage.builder()
                .message(gameMessage)
                .senderPlayer(this.getMyPlayerImage())
                .receiverPlayer(receiverPlayer)
                .build();
        requiredSendingMessages.put(communicationMessage, Instant.now());
    }

    public void handleReceivedUnicastMessage(SnakesProto.GameMessage message, InetAddress senderInetAddress, int senderPort) {
        this.senderInetAddress = senderInetAddress;
        this.senderPort = senderPort;
        if (message != null) {
            switch (message.getTypeCase()) {
                case ACK -> handleAckMessage(message);
                case JOIN -> handleJoinMessage(message);
                case STEER -> handleSteerMessage(message);
                case STATE -> handleStateMessage(message);
                case ROLE_CHANGE -> handleRoleChangeMessage(message);
                default -> sendAckMessageTo(message);
            }
            gameModel.makePlayerTimestamp(message.getSenderId());
        }
    }

    public void handleReceivedMulticastMessage(SnakesProto.GameMessage message, InetAddress senderInetAddress, int senderPort) {
        this.senderInetAddress = senderInetAddress;
        this.senderPort = senderPort;
        if (message != null) {
            var announcementMessageIndicator = SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT;
            if (message.getTypeCase().equals(announcementMessageIndicator)) {
                handleAnnouncementMessage(message);
            }
            gameModel.makePlayerTimestamp(message.getSenderId());
        }
    }

    private void handleAnnouncementMessage(SnakesProto.GameMessage message) {
        var currentMasterPlayer = GamePlayersMaker.getMasterPlayerFromList(message.getAnnouncement().getPlayers());
        currentMasterPlayer = Objects.requireNonNull(currentMasterPlayer).toBuilder()
                .setIpAddress(senderInetAddress.getHostAddress()).build();

        int currentMasterPlayerPort = currentMasterPlayer.getPort();
        CommunicationMessage communicationMessage = CommunicationMessage.builder()
                .message(message)
                .senderPlayer(currentMasterPlayer)
                .receiverPlayer(null).build();
        if (!myInetAddress.equals(senderInetAddress) || myPort != currentMasterPlayerPort) {
            // Удаляем данные о старой игре мастера. Мастер создал новую игру.
            for (var timestamp : announcementsTimestamps.entrySet()) {
                if (timestamp.getKey().getSenderPlayer().getId() == currentMasterPlayer.getId()) {
                    announcementsTimestamps.remove(timestamp.getKey());
                    announcementsTimestamps.put(communicationMessage, Instant.now());
                    break;
                }
            }
            announcementsTimestamps.put(communicationMessage, Instant.now());
        }
    }

    private void handleStateMessage(SnakesProto.GameMessage stateMessage) {
        var actualPlayersList = stateMessage.getState().getState().getPlayers();
        var actualMasterPlayerId = GamePlayersMaker.getMasterPlayerFromList(actualPlayersList);
        if (masterPlayer.getId() == Objects.requireNonNull(actualMasterPlayerId).getId()) {
            gameModel.setGameState(stateMessage.getState().getState());
        }
        this.sendAckMessageTo(stateMessage);
    }

    private void handleRoleChangeMessage(SnakesProto.GameMessage changeRoleMessage) {
        var zombieSnakeIndicator = SnakesProto.GameState.Snake.SnakeState.ZOMBIE;
        if (changeRoleMessage.getRoleChange().hasSenderRole()) {
            if (changeRoleMessage.getRoleChange().getSenderRole().equals(VIEWER_ROLE)) {
                gameModel.changePlayerGameStatus(changeRoleMessage.getSenderId(),
                        changeRoleMessage.getRoleChange().getSenderRole(),
                        zombieSnakeIndicator);
            }
        }
        if (changeRoleMessage.getRoleChange().hasReceiverRole()) {
            if (changeRoleMessage.getRoleChange().getReceiverRole().equals(DEPUTY_ROLE)) {
                nodeRole = DEPUTY_ROLE;
            }
            if (changeRoleMessage.getRoleChange().getReceiverRole().equals(MASTER_ROLE)) {
                nodeRole = MASTER_ROLE;
                gameModel.rebuiltGameModel(nodeId.hashCode());
            }
        }
        this.sendAckMessageTo(changeRoleMessage);
    }

    private void handleSteerMessage(SnakesProto.GameMessage steerMessage) {
        var chosenDirection = steerMessage.getSteer().getDirection();
        gameModel.changeSnakeDirectionById(chosenDirection, steerMessage.getSenderId(), steerMessage.getMsgSeq());
        this.sendAckMessageTo(steerMessage);
    }

    private void handleAckMessage(SnakesProto.GameMessage message) {
        for (var timestamp : requiredSendingMessages.entrySet()) {
            CommunicationMessage messageByTimestamp = timestamp.getKey();
            if (messageByTimestamp.getMessage().getMsgSeq() == message.getMsgSeq() &&
                    message.getSenderId() == messageByTimestamp.getReceiverPlayer().getId()) {
                DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(),
                        "got ack [" + message.getMsgSeq() + "] " +
                                "from [" + message.getSenderId() + "]");
                requiredSendingMessages.remove(messageByTimestamp);
            }
        }

        for (var messageToConfirm : requiredConfirmationMessages.entrySet()) {
            CommunicationMessage correspondingMessage = messageToConfirm.getKey();
            if (correspondingMessage.getMessage().getMsgSeq() == message.getMsgSeq() &&
                    message.getSenderId() == correspondingMessage.getReceiverPlayer().getId()) {
                DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(),
                        "got ack for message\n{\n" + correspondingMessage.getMessage() + "}\n");
                requiredSendingMessages.remove(correspondingMessage);
            }
        }
    }

    private void handleJoinMessage(SnakesProto.GameMessage message) {
        var newPlayer = this.getPlayerImageByMessage(message);
        this.sendAckMessageTo(message);
        gameModel.addNewPlayerToModel(newPlayer);
        if (deputyPlayer == null) {
            deputyPlayer = newPlayer;
            this.sendRoleChangeMessage(newPlayer, MASTER_ROLE, DEPUTY_ROLE);
        }
    }

    public void sendAckMessageTo(SnakesProto.GameMessage message) {
        var ackMessageImage = SnakesProto.GameMessage.AckMsg.newBuilder().build();
        var gameMessage = SnakesProto.GameMessage.newBuilder()
                .setAck(ackMessageImage)
                .setMsgSeq(message.getMsgSeq())
                .setSenderId(nodeId.hashCode())
                .build();
        CommunicationMessage communicationMessage = CommunicationMessage.builder()
                .message(gameMessage)
                .senderPlayer(this.getMyPlayerImage())
                .receiverPlayer(this.getPlayerImageByMessage(message))
                .build();
        requiredSendingMessages.put(communicationMessage, Instant.now());
        DebugPrinter.printWithSpecifiedDateAndName(this.getClass().getSimpleName(),
                "Required Sending Messages has amount [" + requiredSendingMessages.size() + "]");
    }

    private SnakesProto.GamePlayer getPlayerImageByMessage(SnakesProto.GameMessage message) {
        return GamePlayersMaker.buildGamePlayerImage(
                message.getSenderId(),
                message.getJoin().getName(),
                senderPort,
                senderInetAddress.getHostAddress(),
                NORMAL_ROLE);
    }

    public void sendJoinGameMessage(SnakesProto.GamePlayer receiverPlayer) {
        nodeRole = NORMAL_ROLE;
        masterPlayer = receiverPlayer;
        gameModel.setSessionMasterId(masterPlayer.getId());
        var joinMessage = SnakesProto.GameMessage.JoinMsg.newBuilder()
                .setOnlyView(false)
                .setName(this.nodeName)
                .build();
        var gameMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setJoin(joinMessage)
                .setSenderId(nodeId.hashCode())
                .setReceiverId(receiverPlayer.getId())
                .build();
        CommunicationMessage communicationMessage = CommunicationMessage.builder()
                .message(gameMessage)
                .senderPlayer(this.getMyPlayerImage())
                .receiverPlayer(receiverPlayer).build();
        requiredSendingMessages.put(communicationMessage, Instant.now());
    }

    public void sendRoleChangeMessage(SnakesProto.GamePlayer receiverPlayer,
                                      SnakesProto.NodeRole senderPlayerRole,
                                      SnakesProto.NodeRole receiverPlayerRole) {
        if (receiverPlayer == null) {
            receiverPlayer = masterPlayer;
            receiverPlayerRole = MASTER_ROLE;
        }
        var roleChangeMessage = SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                .setSenderRole(senderPlayerRole)
                .setReceiverRole(receiverPlayerRole)
                .build();
        var gameMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setRoleChange(roleChangeMessage)
                .setSenderId(nodeId.hashCode())
                .setReceiverId(receiverPlayer.getId())
                .build();
        CommunicationMessage communicationMessage = CommunicationMessage.builder()
                .message(gameMessage)
                .senderPlayer(this.getMyPlayerImage())
                .receiverPlayer(receiverPlayer).build();
        requiredSendingMessages.put(communicationMessage, Instant.now());
    }

    private SnakesProto.GamePlayer getMyPlayerImage() {
        return GamePlayersMaker.buildGamePlayerImage(
                nodeId.hashCode(),
                nodeName,
                myPort,
                "",
                nodeRole);
    }

    public void sendChangeSnakeDirection(SnakesProto.Direction chosenDirection) {
        if (nodeRole.equals(MASTER_ROLE)) {
            int sessionMasterId = gameModel.getSessionMasterId();
            long masterDirectionChangesNumber = gameModel.getDirectionChangesNumbersByPlayer().get(sessionMasterId);
            masterDirectionChangesNumber++;
            gameModel.changeSnakeDirectionById(chosenDirection,
                    sessionMasterId,
                    masterDirectionChangesNumber);
        }
        if (nodeRole.equals(NORMAL_ROLE) || nodeRole.equals(DEPUTY_ROLE)) {
            this.sendSteerMessage(chosenDirection);
        }
    }

    private void sendSteerMessage(SnakesProto.Direction chosenDirection) {
        var steerMessage = SnakesProto.GameMessage.SteerMsg.newBuilder()
                .setDirection(chosenDirection)
                .build();
        var gameMessage = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setSteer(steerMessage)
                .setSenderId(nodeId.hashCode())
                .setReceiverId(masterPlayer.getId())
                .build();
        CommunicationMessage communicationMessage = CommunicationMessage.builder()
                .message(gameMessage)
                .senderPlayer(this.getMyPlayerImage())
                .receiverPlayer(null)
                .build();
        requiredSendingMessages.put(communicationMessage, Instant.now());
    }

    public static int incrementStateNumber() {
        DebugPrinter.printWithSpecifiedDateAndName(NetworkNode.class.getSimpleName(),
                "State number [" + gameStateNumber + "]");
        return gameStateNumber++;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void updateState() {
        viewController.updateAvailableGames(announcementsTimestamps);
    }

    public void setNewDefaultMasterPlayer() {
        this.masterPlayer = GamePlayersMaker.buildGamePlayerImage(
                nodeId.hashCode(),
                nodeName,
                0,
                myInetAddress.getHostAddress(),
                MASTER_ROLE);
        this.nodeRole = MASTER_ROLE;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getMyPort() {
        return myPort;
    }

}
