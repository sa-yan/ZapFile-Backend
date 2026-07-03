package com.sayan.zapfile.relay;

import com.sayan.zapfile.signaling.WsAuthInterceptor;
import com.sayan.zapfile.transfer.Transfer;
import com.sayan.zapfile.transfer.TransferRepository;
import com.sayan.zapfile.transfer.TransferService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fallback for when a direct P2P connection cannot be established (e.g.
 * both devices behind symmetric NAT). Each device connects to
 * {@code /relay?token=..&deviceId=..&transferId=..}; binary frames from
 * one side are forwarded verbatim to the other, and text frames pass
 * through as a client-defined control channel (EOF/ack markers etc.).
 *
 * Frames are forwarded as they arrive and are NEVER written to disk or
 * accumulated — the server holds at most the in-flight send buffer.
 */
@Component
public class RelayHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RelayHandler.class);

    private static final String ATTR_TRANSFER_ID = "transferId";
    /** Per-session in-flight send buffer; overflowing it (dead peer) closes the session. */
    private static final int SEND_BUFFER_SIZE = 8 * 1024 * 1024;
    private static final int SEND_TIMEOUT_MS = 30_000;

    private final TransferRepository transferRepository;
    private final TransferService transferService;

    /** transferId -> (deviceId -> session) for the (at most two) relay participants. */
    private final Map<String, Map<String, WebSocketSession>> sessionsByTransfer = new ConcurrentHashMap<>();

    public RelayHandler(TransferRepository transferRepository, TransferService transferService) {
        this.transferRepository = transferRepository;
        this.transferService = transferService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = attr(session, WsAuthInterceptor.ATTR_DEVICE_ID);
        String transferId = UriComponentsBuilder.fromUri(session.getUri()).build()
                .getQueryParams().getFirst("transferId");

        Transfer transfer = transferId == null ? null
                : transferRepository.findById(transferId).orElse(null);
        boolean allowed = transfer != null
                && transfer.involvesDevice(deviceId)
                && (transfer.getStatus() == Transfer.Status.ACCEPTED
                    || transfer.getStatus() == Transfer.Status.IN_PROGRESS);
        if (!allowed) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Transfer not relayable for this device"));
            return;
        }

        session.getAttributes().put(ATTR_TRANSFER_ID, transferId);
        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, SEND_BUFFER_SIZE);
        Map<String, WebSocketSession> participants =
                sessionsByTransfer.computeIfAbsent(transferId, k -> new ConcurrentHashMap<>());
        WebSocketSession previous = participants.put(deviceId, safeSession);
        if (previous != null && previous.isOpen()) {
            previous.close();
        }

        transferService.markRelayStarted(transferId);

        WebSocketSession peer = peerOf(transferId, deviceId);
        boolean peerConnected = peer != null && peer.isOpen();
        safeSession.sendMessage(new TextMessage(
                "{\"type\":\"relay.ready\",\"peerConnected\":" + peerConnected + "}"));
        if (peerConnected) {
            peer.sendMessage(new TextMessage("{\"type\":\"relay.peer-joined\"}"));
        }
        log.debug("Relay: device {} joined transfer {} (peer connected: {})",
                deviceId, transferId, peerConnected);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        forward(session, message, true);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        forward(session, message, false);
    }

    private void forward(WebSocketSession session, org.springframework.web.socket.WebSocketMessage<?> message,
                         boolean binary) throws Exception {
        String deviceId = attr(session, WsAuthInterceptor.ATTR_DEVICE_ID);
        String transferId = attr(session, ATTR_TRANSFER_ID);
        if (transferId == null) {
            return; // connection was rejected in afterConnectionEstablished
        }
        WebSocketSession peer = peerOf(transferId, deviceId);
        if (peer == null || !peer.isOpen()) {
            if (!binary) {
                // don't answer every dropped binary frame; one text-level error is enough
                session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"Peer not connected\"}"));
            }
            return;
        }
        peer.sendMessage(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String deviceId = attr(session, WsAuthInterceptor.ATTR_DEVICE_ID);
        String transferId = attr(session, ATTR_TRANSFER_ID);
        if (transferId == null) {
            return;
        }
        Map<String, WebSocketSession> participants = sessionsByTransfer.get(transferId);
        if (participants != null) {
            participants.computeIfPresent(deviceId,
                    (k, s) -> s.getId().equals(session.getId()) ? null : s);
            sessionsByTransfer.computeIfPresent(transferId,
                    (k, m) -> m.isEmpty() ? null : m);
        }
        WebSocketSession peer = peerOf(transferId, deviceId);
        if (peer != null && peer.isOpen()) {
            peer.sendMessage(new TextMessage("{\"type\":\"relay.peer-left\"}"));
        }
        log.debug("Relay: device {} left transfer {} ({})", deviceId, transferId, status);
    }

    private WebSocketSession peerOf(String transferId, String deviceId) {
        Map<String, WebSocketSession> participants = sessionsByTransfer.get(transferId);
        if (participants == null) {
            return null;
        }
        return participants.entrySet().stream()
                .filter(e -> !e.getKey().equals(deviceId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String attr(WebSocketSession session, String key) {
        return (String) session.getAttributes().get(key);
    }
}
