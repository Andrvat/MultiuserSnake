package app.networks;

import java.net.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import app.model.GameModel;
import app.view.ViewController;
import app.utilities.CommunicationMessage;
import app.utilities.MulticastReceiver;
import app.utilities.Subscriber;
import app.utilities.UnicastReceiver;
import proto.SnakesProto;

public class NetworkNode extends Subscriber {
    private final String nodeName;
    private SnakesProto.NodeRole nodeRole;

    private static final String multicastIP = "239.192.0.4";
    private static final int multicastPort = 9192;
    private InetAddress senderIp;
    private int senderPort;
    private static final int timeout = 3000;
    private final InetAddress myAddress;
    private final int myPort;
    private final MulticastSocket multicastSocket;
    private final DatagramSocket datagramSocket;

    private final GameModel gameModel;
    private final ViewController viewController;

    private SnakesProto.GamePlayer master;
    private SnakesProto.GamePlayer deputy = null;

    private static int stateNumber = 0;
    private long lastTimeForAnn, lastTimeForState, getLastTimeForSend;

    private final ConcurrentHashMap<CommunicationMessage, Instant> announcements = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<CommunicationMessage, Instant> messages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CommunicationMessage, Instant> toResponse = new ConcurrentHashMap<>();

    DatagramPacket datagramPacket;
    public UUID nodeID;

    public String getNodeName() {
        return nodeName;
    }

    public int getMyPort() {
        return myPort;
    }

    public NetworkNode(GameModel gameModel, SnakesProto.NodeRole nodeRole, String name, InetAddress address, int portN, UUID uuid) throws Exception {
        super(gameModel);

        this.nodeID = uuid;
        myAddress = address;
        myPort = portN;

        this.gameModel = gameModel;
        viewController = new ViewController(this.gameModel, this);
        this.nodeRole = nodeRole;
        this.nodeName = name;

        multicastSocket = new MulticastSocket(multicastPort);
        multicastSocket.joinGroup(InetAddress.getByName(multicastIP));
        multicastSocket.setSoTimeout(timeout);

        datagramSocket = new DatagramSocket(portN, address);
        datagramSocket.setSoTimeout(timeout);
    }

    public void start() {
        UnicastReceiver unicastReceiver = new UnicastReceiver(datagramSocket, this);
        MulticastReceiver multicastReceiver = new MulticastReceiver(multicastSocket, this);

        lastTimeForAnn = Instant.now().toEpochMilli();
        lastTimeForState = Instant.now().toEpochMilli();
        getLastTimeForSend = Instant.now().toEpochMilli();

        unicastReceiver.start();
        multicastReceiver.start();

        while (true) {
            sendMessages();
        }
    }

    public static int incState() {
        System.out.println("stateNumber = " + stateNumber);
        return stateNumber++;
    }

    private boolean isTimeTo(long time, int period) {
        return Instant.now().toEpochMilli() - time > period;
    }

    private void sendMessages() {
        synchronized (messages) {
            sendMail();
        }
        sendResponses();
        if (isTimeTo(lastTimeForAnn, 3000)) {
            if (nodeRole == SnakesProto.NodeRole.MASTER) {
                sendAnnouncements();
                lastTimeForAnn = Instant.now().toEpochMilli();
            }
        }
    }

    private void sendResponses() {
        if (isTimeTo(getLastTimeForSend, gameModel.getGameState().getConfig().getPingDelayMs())) {
            checkPlayerActivity();
            getLastTimeForSend = Instant.now().toEpochMilli();
            System.gc();
        } else return;

        for (Map.Entry<CommunicationMessage, Instant> entry : messages.entrySet()) {
            try {
                CommunicationMessage m = entry.getKey();
                if (m.to == null) {
                    if (master != null)
                        m.to = master;
                    else {
                        messages.remove(m);
                        continue;
                    }
                }

                m.gameMessage = m.gameMessage.toBuilder().
                        setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(gameModel.getGameState())).build();

                datagramPacket =
                        new DatagramPacket(m.gameMessage.toByteArray(),
                                m.gameMessage.toByteArray().length,
                                InetAddress.getByName(m.to.getIpAddress()),
                                m.to.getPort());
                datagramSocket.send(datagramPacket);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMail() {
        boolean isTime = false;
        int time = gameModel.getGameState().getConfig().getStateDelayMs();
        if (isTimeTo(lastTimeForState, time) && nodeRole == SnakesProto.NodeRole.MASTER) {
            gameModel.computeNextStep();
            deputy = GameModel.getDeputy(gameModel.getGameState().getPlayers());

            for (SnakesProto.GamePlayer player : gameModel.getGameState().getPlayers().getPlayersList()) {
                if (nodeID.hashCode() != player.getId()) {
                    messages.put(new CommunicationMessage(null, master, player), Instant.now());
                }
            }
            isTime = true;
            lastTimeForState = Instant.now().toEpochMilli();
        }

        for (Map.Entry<CommunicationMessage, Instant> entry : messages.entrySet()) {
            try {
                CommunicationMessage m = entry.getKey();
                if (m.to == null) {
                    if (master != null)
                        m.to = master;
                    else {
                        messages.remove(m);
                        continue;
                    }
                }

                if (m.gameMessage == null) {
                    m.gameMessage = SnakesProto.GameMessage.newBuilder()
                            .setMsgSeq(incState())
                            .setSenderId(nodeID.hashCode())
                            .setState(SnakesProto.GameMessage.StateMsg.newBuilder().setState(gameModel.getGameState()).build())
                            .build();
                }

                datagramPacket = new DatagramPacket(m.gameMessage.toByteArray(), m.gameMessage.toByteArray().length, InetAddress.getByName(m.to.getIpAddress()), m.to.getPort());

                if (m.gameMessage.getTypeCase() == SnakesProto.GameMessage.TypeCase.STATE) {

                    if (isTime) {
                        datagramSocket.send(datagramPacket);
                    }
                } else {
                    datagramSocket.send(datagramPacket);
                    if (m.gameMessage.getTypeCase() != SnakesProto.GameMessage.TypeCase.ACK) {
                        toResponse.put(m, Instant.now());
                    }
                    messages.remove(m);
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
        for (Map.Entry<Integer, Instant> entry : gameModel.getPlayerActivitiesTimestamps().entrySet()) {
            if (Instant.now().toEpochMilli() - entry.getValue().toEpochMilli() > gameModel.getGameState().getConfig().getNodeTimeoutMs()) {

                if (gameModel.getPlayer(entry.getKey()).getRole() == SnakesProto.NodeRole.MASTER) {
                    if (nodeRole == SnakesProto.NodeRole.DEPUTY) {
                        gameModel.repair(nodeID.hashCode());
                        nodeRole = SnakesProto.NodeRole.MASTER;
                        for (SnakesProto.GamePlayer player : gameModel.getGameState().getPlayers().getPlayersList()) {
                            sendChangeRoleMsg(player, SnakesProto.NodeRole.MASTER, SnakesProto.NodeRole.NORMAL);
                        }
                    }
                    if (nodeRole == SnakesProto.NodeRole.NORMAL) {
                        master = GameModel.getDeputy(gameModel.getGameState().getPlayers());
                    }
                }
                sendPingMsg(gameModel.getPlayer(entry.getKey()));
            }
        }
    }

    private void sendAnnouncements() {
        byte[] data;
        SnakesProto.GameMessage.AnnouncementMsg announcementMsg = SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                .setCanJoin(true)
                .setPlayers(gameModel.getSessionGamePlayers())
                .setConfig(gameModel.getConfig())
                .build();
        SnakesProto.GameMessage mess = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(0)
                .setSenderId(0)
                .setReceiverId(0)
                .setAnnouncement(announcementMsg)
                .build();

        try {
            data = mess.toByteArray();
            datagramPacket = new DatagramPacket(data, data.length, InetAddress.getByName(multicastIP), multicastPort);
            multicastSocket.send(datagramPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void receiveUnicast(SnakesProto.GameMessage message, InetAddress senderIp, int senderPort) {
        this.senderIp = senderIp;
        this.senderPort = senderPort;
        if (message != null) {
            switch (message.getTypeCase()) {
                case ACK -> askHandler(message);
                case JOIN -> joinHandler(message);
                case STEER -> steerHandler(message);
                case STATE -> stateHandler(message);
                case ROLE_CHANGE -> roleChangeHandler(message);
                default -> createAndPutAck(message);
            }
            gameModel.updatePlayer(message.getSenderId());
        }
    }

    public void receiveMulticast(SnakesProto.GameMessage message, InetAddress senderIp, int senderPort) {
        this.senderIp = senderIp;
        this.senderPort = senderPort;
        if (message != null) {
            if (message.getTypeCase() == SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT) {
                announcementHandler(message);
            }
            gameModel.updatePlayer(message.getSenderId());
        }
    }

    private void stateHandler(SnakesProto.GameMessage message) {
        if (master.getId() == Objects.requireNonNull(GameModel.getMaster(message.getState().getState().getPlayers())).getId())
            gameModel.setGameState(message.getState().getState());
        createAndPutAck(message);
    }

    private void announcementHandler(SnakesProto.GameMessage announcementMsg) {
        SnakesProto.GamePlayer master = GameModel.getMaster(announcementMsg.getAnnouncement().getPlayers());
        master = Objects.requireNonNull(master).toBuilder().setIpAddress(senderIp.getHostAddress()).build();

        int masterPort = master.getPort();
        CommunicationMessage communicationMessage = new CommunicationMessage(announcementMsg, master, null);
        if (senderIp.equals(myAddress) && myPort == masterPort) {
            return;
        }

        for (Map.Entry<CommunicationMessage, Instant> entry : announcements.entrySet()) {
            if (entry.getKey().from.getId() == master.getId()) {
                announcements.remove(entry.getKey());
                announcements.put(communicationMessage, Instant.now());
                break;
            }
        }
        announcements.put(communicationMessage, Instant.now());
    }

    private void roleChangeHandler(SnakesProto.GameMessage message) {
        if (message.getRoleChange().hasSenderRole()) {
            if (message.getRoleChange().getSenderRole() == SnakesProto.NodeRole.VIEWER) {
                gameModel.changeRole(message.getSenderId(), message.getRoleChange().getSenderRole(), SnakesProto.GameState.Snake.SnakeState.ZOMBIE);
            }
        }
        if (message.getRoleChange().hasReceiverRole()) {
            if (message.getRoleChange().getReceiverRole() == SnakesProto.NodeRole.DEPUTY) {
                nodeRole = SnakesProto.NodeRole.DEPUTY;
            }
            if (message.getRoleChange().getReceiverRole() == SnakesProto.NodeRole.MASTER) {
                nodeRole = SnakesProto.NodeRole.MASTER;
                gameModel.repair(nodeID.hashCode());
            }
        }
        createAndPutAck(message);
    }

    private void steerHandler(SnakesProto.GameMessage message) {
        SnakesProto.Direction d = message.getSteer().getDirection();
        gameModel.changeDirection(d, message.getSenderId(), message.getMsgSeq());
        createAndPutAck(message);
    }

    private void askHandler(SnakesProto.GameMessage message) {
        System.out.println("sizebefore = " + messages.size());
        for (Map.Entry<CommunicationMessage, Instant> entry : messages.entrySet()) {
            CommunicationMessage m = entry.getKey();
            if (m.gameMessage.getMsgSeq() == message.getMsgSeq() && message.getSenderId() == m.getTo().getId()) {
                System.out.println("f = " + message.getMsgSeq());
                System.out.println("delete");
                messages.remove(m);
            }
        }

        for (Map.Entry<CommunicationMessage, Instant> entry : toResponse.entrySet()) {
            CommunicationMessage m = entry.getKey();
            if (m.gameMessage.getMsgSeq() == message.getMsgSeq() && message.getSenderId() == m.getTo().getId()) {
                System.out.println("deleteRe");
                messages.remove(m);
            }
        }
        System.out.println("size = " + messages.size());
    }

    private void joinHandler(SnakesProto.GameMessage message) {
        SnakesProto.GamePlayer newPlayer = GameModel.makePlayer(message.getSenderId(), "", senderPort, senderIp.getHostAddress(), SnakesProto.NodeRole.NORMAL);
        createAndPutAck(message);
        if (gameModel.addPlayer(newPlayer) == 1) return;
        if (deputy == null) {
            deputy = newPlayer;
            sendChangeRoleMsg(newPlayer, SnakesProto.NodeRole.MASTER, SnakesProto.NodeRole.DEPUTY);
        }
    }

    public void createAndPutAck(SnakesProto.GameMessage message) {
        SnakesProto.GameMessage.AckMsg ackMsg = SnakesProto.GameMessage.AckMsg.newBuilder().build();
        SnakesProto.GameMessage mess = SnakesProto.GameMessage.newBuilder()
                .setAck(ackMsg)
                .setMsgSeq(message.getMsgSeq())
                .setSenderId(nodeID.hashCode())
                .build();
        CommunicationMessage communicationMessage1 = new CommunicationMessage(mess, GameModel.makePlayer(nodeID.hashCode(), nodeName, myPort, "", nodeRole),
                GameModel.makePlayer(message.getSenderId(), "", senderPort, senderIp.getHostAddress(), SnakesProto.NodeRole.NORMAL));
        messages.put(communicationMessage1, Instant.now());

        System.out.println(messages.size());
    }

    public void sendJoinGame(SnakesProto.GamePlayer to, SnakesProto.GameConfig config) {
        nodeRole = SnakesProto.NodeRole.NORMAL;
        master = to;
        try {
            gameModel.setMasterIp(master.getId());
        } catch (Exception ignored) {
        }

        SnakesProto.GameMessage.JoinMsg joinMsg = SnakesProto.GameMessage.JoinMsg.newBuilder()
                .setOnlyView(false)
                .setName("placeholder")
                .build();
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incState())
                .setJoin(joinMsg)
                .setSenderId(nodeID.hashCode())
                .setReceiverId(to.getId())
                .build();
        CommunicationMessage mes = new CommunicationMessage(message, GameModel.makePlayer(nodeID.hashCode(), nodeName, myPort, "", nodeRole), to);
        synchronized (messages) {
            messages.put(mes, Instant.now());
        }
    }

    public void sendPingMsg(SnakesProto.GamePlayer to) {
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incState())
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .setSenderId(nodeID.hashCode())
                .setReceiverId(to.getId())
                .build();
        CommunicationMessage mes = new CommunicationMessage(message, GameModel.makePlayer(nodeID.hashCode(), nodeName, myPort, "", nodeRole), to);
        messages.put(mes, Instant.now());
    }

    public void sendChangeRoleMsg(SnakesProto.GamePlayer to, SnakesProto.NodeRole srole, SnakesProto.NodeRole rrole) {
        if (to == null) to = master;
        SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                .setSenderRole(srole)
                .setReceiverRole(rrole)
                .build();
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incState())
                .setRoleChange(roleChangeMsg)
                .setSenderId(nodeID.hashCode())
                .setReceiverId(to.getId())
                .build();
        CommunicationMessage mes = new CommunicationMessage(message, GameModel.makePlayer(nodeID.hashCode(), nodeName, myPort, "", nodeRole), to);
        messages.put(mes, Instant.now());
    }

    public void sendSteerMsg(SnakesProto.Direction d) {
        SnakesProto.GameMessage.SteerMsg steerMsg = SnakesProto.GameMessage.SteerMsg.newBuilder()
                .setDirection(d)
                .build();
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                .setMsgSeq(incState())
                .setSteer(steerMsg)
                .setSenderId(nodeID.hashCode())
                .setReceiverId(master.getId())
                .build();
        CommunicationMessage mes = new CommunicationMessage(message, GameModel.makePlayer(nodeID.hashCode(), nodeName, myPort, "", nodeRole), null);
        messages.put(mes, Instant.now());
    }

    public void Notify(int x) {
        viewController.updateAnn(announcements);
    }

    public void changeDirection(SnakesProto.Direction d) {
        if (nodeRole == SnakesProto.NodeRole.MASTER) {
            gameModel.changeDirection(d, gameModel.getSessionMasterId(), gameModel.getChangeHelper().get(gameModel.getSessionMasterId()) + 1);
        }
        if (nodeRole == SnakesProto.NodeRole.NORMAL || nodeRole == SnakesProto.NodeRole.DEPUTY) {
            sendSteerMsg(d);
        }
    }

    public void changeRole(SnakesProto.NodeRole nodeRole) {
        master = GameModel.makePlayer(nodeID.hashCode(), nodeName, 0, myAddress.getHostAddress(), SnakesProto.NodeRole.MASTER);
        this.nodeRole = nodeRole;
    }
}
