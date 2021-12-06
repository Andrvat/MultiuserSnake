package app.networks;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.Map;
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
    private static final int TIMEOUT = 3000;

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

        viewController = new ViewController(this.gameModel, this);

        multicastSocket = new MulticastSocket(MULTICAST_PORT);
        datagramSocket = new DatagramSocket(myPort, myInetAddress);
        connectMulticastSocketToGroup();
        setTimeoutsForSockets();
    }

    private void connectMulticastSocketToGroup() throws IOException {
        multicastSocket.joinGroup(InetAddress.getByName(MULTICAST_IP));
    }

    private void setTimeoutsForSockets() throws SocketException {
        multicastSocket.setSoTimeout(TIMEOUT);
        datagramSocket.setSoTimeout(TIMEOUT);
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
            sendMessages();
        }
    }

    private long getEpochMillisBySystemClockInstant() {
        return Instant.now().toEpochMilli();
    }

    private boolean isTimeTo(long time, int period) {
        return Instant.now().toEpochMilli() - time > period;
    }

    private void sendMessages() {
        sendMail();

        sendResponses();
        if (isTimeTo(lastAnnouncementTimestamp, 3000)) {
            if (nodeRole == SnakesProto.NodeRole.MASTER) {
                sendAnnouncements();
                lastAnnouncementTimestamp = Instant.now().toEpochMilli();
            }
        }
    }

    private void sendResponses() {
        if (isTimeTo(lastSentMessageTimestamp, gameModel.getGameState().getConfig().getPingDelayMs())) {
            checkPlayerActivity();
            lastSentMessageTimestamp = Instant.now().toEpochMilli();
            System.gc();
        } else return;

        for (Map.Entry<CommunicationMessage, Instant> entry : requiredSendingMessages.entrySet()) {
            try {
                CommunicationMessage m = entry.getKey();
                if (m.getReceiverPlayer() == null) {
                    if (masterPlayer != null)
                        m.setReceiverPlayer(masterPlayer);
                    else {
                        requiredSendingMessages.remove(m);
                        continue;
                    }
                }

                m.setMessage(m.getMessage().toBuilder().
                        setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(gameModel.getGameState())).build());

                sendingDatagramPacket =
                        new DatagramPacket(m.getMessage().toByteArray(),
                                m.getMessage().toByteArray().length,
                                InetAddress.getByName(m.getReceiverPlayer().getIpAddress()),
                                m.getReceiverPlayer().getPort());
                datagramSocket.send(sendingDatagramPacket);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMail() {
        boolean isTime = false;
        int time = gameModel.getGameState().getConfig().getStateDelayMs();
        if (isTimeTo(lastStateTimestamp, time) && nodeRole == SnakesProto.NodeRole.MASTER) {
            gameModel.makeGameNextStep();
            deputyPlayer = GamePlayersMaker.getDeputyPlayerFromList(gameModel.getGameState().getPlayers());
            for (SnakesProto.GamePlayer player : gameModel.getGameState().getPlayers().getPlayersList()) {
                if (nodeId.hashCode() != player.getId()) {
                    requiredSendingMessages.put(CommunicationMessage.builder()
                            .message(null)
                            .senderPlayer(masterPlayer)
                            .receiverPlayer(player).build(), Instant.now());
                }
            }
            isTime = true;
            lastStateTimestamp = Instant.now().toEpochMilli();
        }

        for (Map.Entry<CommunicationMessage, Instant> entry : requiredSendingMessages.entrySet()) {
            try {
                CommunicationMessage m = entry.getKey();
                if (m.getReceiverPlayer() == null) {
                    if (masterPlayer != null)
                        m.setReceiverPlayer(masterPlayer);
                    else {
                        requiredSendingMessages.remove(m);
                        continue;
                    }
                }

                if (m.getMessage() == null) {
                    m.setMessage(SnakesProto.GameMessage.newBuilder()
                            .setMsgSeq(incrementStateNumber())
                            .setSenderId(nodeId.hashCode())
                            .setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(gameModel.getGameState()).build())
                            .build());
                }

                sendingDatagramPacket = new DatagramPacket(
                        m.getMessage().toByteArray(), m.getMessage().toByteArray().length, InetAddress.getByName(m.getReceiverPlayer().getIpAddress()), m.getReceiverPlayer().getPort());

                if (m.getMessage().getTypeCase() == SnakesProto.GameMessage.TypeCase.STATE) {

                    if (isTime) {
                        datagramSocket.send(sendingDatagramPacket);
                    }
                } else {
                    datagramSocket.send(sendingDatagramPacket);
                    if (m.getMessage().getTypeCase() != SnakesProto.GameMessage.TypeCase.ACK) {
                        requiredConfirmationMessages.put(m, Instant.now());
                    }
                    requiredSendingMessages.remove(m);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (isTime) {
            System.gc();
        }
    }

    private void checkPlayerActivity() {
        for (Map.Entry<Integer, Instant> entry : gameModel.getActivitiesTimestampsByPlayer().entrySet()) {
            if (Instant.now().toEpochMilli() - entry.getValue().toEpochMilli() > gameModel.getGameState().getConfig().getNodeTimeoutMs()) {

                if (gameModel.getPlayerById(entry.getKey()).getRole() == SnakesProto.NodeRole.MASTER) {
                    if (nodeRole == SnakesProto.NodeRole.DEPUTY) {
                        gameModel.rebuiltGameModel(nodeId.hashCode());
                        nodeRole = SnakesProto.NodeRole.MASTER;
                        for (SnakesProto.GamePlayer player : gameModel.getGameState().getPlayers().getPlayersList()) {
                            sendRoleChangeMessage(player, SnakesProto.NodeRole.MASTER, SnakesProto.NodeRole.NORMAL);
                        }
                    }
                    if (nodeRole == SnakesProto.NodeRole.NORMAL) {
                        masterPlayer = GamePlayersMaker.getDeputyPlayerFromList(gameModel.getGameState().getPlayers());
                    }
                }
                sendPingMsg(gameModel.getPlayerById(entry.getKey()));
            }
        }
    }

    private void sendAnnouncements() {
        byte[] data;
        SnakesProto.GameMessage.AnnouncementMsg announcementMsg = SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                .setCanJoin(true)
                .setPlayers(gameModel.getSessionGamePlayers())
                .setConfig(gameModel.getGameConfig())
                .build();
        SnakesProto.GameMessage mess = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(0)
                .setSenderId(0)
                .setReceiverId(0)
                .setAnnouncement(announcementMsg)
                .build();

        try {
            data = mess.toByteArray();
            sendingDatagramPacket = new DatagramPacket(data, data.length, InetAddress.getByName(MULTICAST_IP), MULTICAST_PORT);
            multicastSocket.send(sendingDatagramPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleReceivedUnicastMessage(SnakesProto.GameMessage message, InetAddress senderInetAddress, int senderPort) {
        this.senderInetAddress = senderInetAddress;
        this.senderPort = senderPort;
        if (message != null) {
            switch (message.getTypeCase()) {
                case ACK -> handleAckMessage(message);
                case JOIN -> handleJoinMessage(message);
                case STEER -> steerHandler(message);
                case STATE -> stateHandler(message);
                case ROLE_CHANGE -> roleChangeHandler(message);
                default -> sendAckMessage(message);
            }
            gameModel.makePlayerTimestamp(message.getSenderId());
        }
    }

    public void handleReceivedMulticastMessage(SnakesProto.GameMessage message, InetAddress senderInetAddress, int senderPort) {
        this.senderInetAddress = senderInetAddress;
        this.senderPort = senderPort;
        if (message != null) {
            var announcementMessageIndicator = SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT;
            if (message.getTypeCase() == SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT) {
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

    private void stateHandler(SnakesProto.GameMessage message) {
        if (masterPlayer.getId() == Objects.requireNonNull(GamePlayersMaker.getMasterPlayerFromList(message.getState().getState().getPlayers())).getId())
            gameModel.setGameState(message.getState().getState());
        sendAckMessage(message);
    }

    private void roleChangeHandler(SnakesProto.GameMessage message) {
        if (message.getRoleChange().hasSenderRole()) {
            if (message.getRoleChange().getSenderRole() == SnakesProto.NodeRole.VIEWER) {
                gameModel.changePlayerGameStatus(message.getSenderId(), message.getRoleChange().getSenderRole(), SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
            }
        }
        if (message.getRoleChange().hasReceiverRole()) {
            if (message.getRoleChange().getReceiverRole() == SnakesProto.NodeRole.DEPUTY) {
                nodeRole = SnakesProto.NodeRole.DEPUTY;
            }
            if (message.getRoleChange().getReceiverRole() == SnakesProto.NodeRole.MASTER) {
                nodeRole = SnakesProto.NodeRole.MASTER;
                gameModel.rebuiltGameModel(nodeId.hashCode());
            }
        }
        sendAckMessage(message);
    }

    private void steerHandler(SnakesProto.GameMessage message) {
        SnakesProto.Direction d = message.getSteer().getDirection();
        gameModel.changeSnakeDirectionById(d, message.getSenderId(), message.getMsgSeq());
        sendAckMessage(message);
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
                        "got ack for message\n" + correspondingMessage.getMessage());
                requiredSendingMessages.remove(correspondingMessage);
            }
        }
    }

    private void handleJoinMessage(SnakesProto.GameMessage message) {
        var newPlayer = this.getPlayerImageByMessage(message);
        this.sendAckMessage(message);
        gameModel.addNewPlayerToModel(newPlayer);
        if (deputyPlayer == null) {
            deputyPlayer = newPlayer;
            this.sendRoleChangeMessage(newPlayer, SnakesProto.NodeRole.MASTER, SnakesProto.NodeRole.DEPUTY);
        }
    }

    public void sendAckMessage(SnakesProto.GameMessage message) {
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

    public void sendJoinGame(SnakesProto.GamePlayer to, SnakesProto.GameConfig config) {
        nodeRole = SnakesProto.NodeRole.NORMAL;
        masterPlayer = to;
        try {
            gameModel.setSessionMasterId(masterPlayer.getId());
        } catch (Exception ignored) {
        }

        SnakesProto.GameMessage.JoinMsg joinMsg = SnakesProto.GameMessage.JoinMsg.newBuilder()
                .setOnlyView(false)
                .setName("placeholder")
                .build();
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setJoin(joinMsg)
                .setSenderId(nodeId.hashCode())
                .setReceiverId(to.getId())
                .build();
        CommunicationMessage mes = CommunicationMessage.builder()
                .message(message)
                .senderPlayer(GamePlayersMaker.buildGamePlayerImage(nodeId.hashCode(), nodeName, myPort, "", nodeRole))
                .receiverPlayer(to).build();
        requiredSendingMessages.put(mes, Instant.now());
    }

    public void sendPingMsg(SnakesProto.GamePlayer to) {
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .setSenderId(nodeId.hashCode())
                .setReceiverId(to.getId())
                .build();
        CommunicationMessage mes = CommunicationMessage.builder()
                .message(message)
                .senderPlayer(GamePlayersMaker.buildGamePlayerImage(nodeId.hashCode(), nodeName, myPort, "", nodeRole))
                .receiverPlayer(to).build();
        requiredSendingMessages.put(mes, Instant.now());
    }

    public void sendRoleChangeMessage(SnakesProto.GamePlayer receiverPlayer,
                                      SnakesProto.NodeRole senderPlayerRole,
                                      SnakesProto.NodeRole receiverPlayerRole) {
        if (receiverPlayer == null) {
            receiverPlayer = masterPlayer;
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

    public void sendSteerMsg(SnakesProto.Direction d) {
        SnakesProto.GameMessage.SteerMsg steerMsg = SnakesProto.GameMessage.SteerMsg.newBuilder()
                .setDirection(d)
                .build();
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incrementStateNumber())
                .setSteer(steerMsg)
                .setSenderId(nodeId.hashCode())
                .setReceiverId(masterPlayer.getId())
                .build();
        CommunicationMessage mes = CommunicationMessage.builder()
                .message(message)
                .senderPlayer(GamePlayersMaker.buildGamePlayerImage(nodeId.hashCode(), nodeName, myPort, "", nodeRole))
                .receiverPlayer(null).build();
        requiredSendingMessages.put(mes, Instant.now());
    }

    private SnakesProto.GamePlayer getMyPlayerImage() {
        return GamePlayersMaker.buildGamePlayerImage(
                nodeId.hashCode(),
                nodeName,
                myPort,
                "",
                nodeRole);
    }

    private SnakesProto.GamePlayer getPlayerImageByMessage(SnakesProto.GameMessage message) {
        return GamePlayersMaker.buildGamePlayerImage(
                message.getSenderId(),
                message.getJoin().getName(),
                senderPort,
                senderInetAddress.getHostAddress(),
                SnakesProto.NodeRole.NORMAL);
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public static int incrementStateNumber() {
        DebugPrinter.printWithSpecifiedDateAndName(NetworkNode.class.getSimpleName(), "State number [" + gameStateNumber + "]");
        return gameStateNumber++;
    }

    public void update() {
        viewController.updateAnn(announcementsTimestamps);
    }

    public void changeDirection(SnakesProto.Direction d) {
        if (nodeRole == SnakesProto.NodeRole.MASTER) {
            gameModel.changeSnakeDirectionById(d, gameModel.getSessionMasterId(),
                    gameModel.getDirectionChangesNumbersByPlayer().get(gameModel.getSessionMasterId()) + 1);
        }
        if (nodeRole == SnakesProto.NodeRole.NORMAL || nodeRole == SnakesProto.NodeRole.DEPUTY) {
            sendSteerMsg(d);
        }
    }

    public void changeRole(SnakesProto.NodeRole nodeRole) {
        masterPlayer = GamePlayersMaker.buildGamePlayerImage(nodeId.hashCode(), nodeName, 0, myInetAddress.getHostAddress(), SnakesProto.NodeRole.MASTER);
        this.nodeRole = nodeRole;
    }

    public ConcurrentHashMap<CommunicationMessage, Instant> getRequiredSendingMessages() {
        return requiredSendingMessages;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getMyPort() {
        return myPort;
    }

}
