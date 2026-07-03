package com.sayan.zapfile.signaling;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.sayan.zapfile.device.Device;
import com.sayan.zapfile.presence.PresenceService;
import com.sayan.zapfile.presence.WsSessionRegistry;
import com.sayan.zapfile.transfer.Transfer;
import com.sayan.zapfile.transfer.TransferRepository;
import com.sayan.zapfile.transfer.TransferService;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * The relay at the heart of ZapFile. Devices exchange WebRTC session
 * descriptions and ICE candidates through here (plus live progress
 * updates); the server forwards messages between the two devices bound
 * to a transfer without inspecting payloads. File bytes never touch
 * this server.
 *
 * Protocol (JSON): {"type": "...", "data": {...}}
 *   client -> server: signal.sdp-offer | signal.sdp-answer | signal.ice-candidate
 *                     | transfer.progress   (all require data.transferId)
 *                     | ping
 *   server -> client: same signal.* / transfer.progress relayed (data.fromDeviceId added),
 *                     transfer.offer/accepted/declined/cancelled/completed/failed,
 *                     presence.update, pong, error
 */
@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingHandler.class);

    private static final Set<String> RELAY_TYPES = Set.of(
            "signal.sdp-offer", "signal.sdp-answer", "signal.ice-candidate", "transfer.progress");

    /** SDP payloads are a few KB; 256 KB leaves generous headroom. */
    private static final int SEND_BUFFER_SIZE = 256 * 1024;
    private static final int SEND_TIMEOUT_MS = 10_000;

    private final WsSessionRegistry registry;
    private final PresenceService presenceService;
    private final TransferRepository transferRepository;
    private final TransferService transferService;
    private final ObjectMapper objectMapper;

    public SignalingHandler(WsSessionRegistry registry,
                            PresenceService presenceService,
                            TransferRepository transferRepository,
                            TransferService transferService,
                            ObjectMapper objectMapper) {
        this.registry = registry;
        this.presenceService = presenceService;
        this.transferRepository = transferRepository;
        this.transferService = transferService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = attr(session, WsAuthInterceptor.ATTR_USER_ID);
        String deviceId = attr(session, WsAuthInterceptor.ATTR_DEVICE_ID);
        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, SEND_BUFFER_SIZE);
        registry.register(userId, deviceId, safeSession);
        presenceService.broadcastPresence(userId, true);
        log.debug("Device {} of user {} connected", deviceId, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = attr(session, WsAuthInterceptor.ATTR_USER_ID);
        String deviceId = attr(session, WsAuthInterceptor.ATTR_DEVICE_ID);
        registry.unregister(userId, deviceId, session);
        if (!registry.isUserOnline(userId)) {
            presenceService.broadcastPresence(userId, false);
        }
        log.debug("Device {} of user {} disconnected ({})", deviceId, userId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String deviceId = attr(session, WsAuthInterceptor.ATTR_DEVICE_ID);
        try {
            JsonNode envelope = objectMapper.readTree(message.getPayload());
            String type = envelope.path("type").asText("");
            JsonNode data = envelope.path("data");

            if ("ping".equals(type)) {
                registry.sendToDevice(deviceId, "pong", Map.of());
                return;
            }
            if (RELAY_TYPES.contains(type)) {
                relay(deviceId, type, data);
                return;
            }
            registry.sendToDevice(deviceId, "error", Map.of("message", "Unknown message type: " + type));
        } catch (Exception e) {
            log.warn("Bad WS message from device {}: {}", deviceId, e.getMessage());
            registry.sendToDevice(deviceId, "error", Map.of("message", "Malformed message"));
        }
    }

    private void relay(String fromDeviceId, String type, JsonNode data) {
        String transferId = data.path("transferId").asText("");
        if (transferId.isEmpty()) {
            registry.sendToDevice(fromDeviceId, "error", Map.of("message", "transferId is required"));
            return;
        }
        Transfer transfer = transferRepository.findById(transferId).orElse(null);
        if (transfer == null || !transfer.involvesDevice(fromDeviceId)) {
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Unknown transfer or device not part of it", "transferId", transferId));
            return;
        }
        Transfer.Status status = transfer.getStatus();
        if (status != Transfer.Status.ACCEPTED && status != Transfer.Status.IN_PROGRESS) {
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Transfer is " + status + "; signaling not allowed", "transferId", transferId));
            return;
        }

        if ("transfer.progress".equals(type)) {
            transferService.markProgress(transferId, data.path("bytesTransferred").asLong(0));
        }

        Device peer = transfer.deviceOtherThan(fromDeviceId);
        if (peer == null) {
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Peer device not assigned yet", "transferId", transferId));
            return;
        }
        ObjectNode forwarded = (ObjectNode) data.deepCopy();
        forwarded.put("fromDeviceId", fromDeviceId);
        boolean delivered = registry.sendToDevice(peer.getId(), type, forwarded);
        if (!delivered) {
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Peer is offline", "transferId", transferId));
        }
    }

    private String attr(WebSocketSession session, String key) {
        return (String) session.getAttributes().get(key);
    }
}
