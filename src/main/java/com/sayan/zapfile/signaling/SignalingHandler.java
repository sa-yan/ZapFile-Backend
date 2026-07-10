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
import java.util.concurrent.ConcurrentHashMap;
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

    /** Session attribute holding the per-session Map&lt;transferId, CachedTransfer&gt;. */
    private static final String ATTR_TRANSFER_CACHE = "zapfile.transferCache";
    /** How long a cached authorization is trusted before re-checking the transfer status. */
    private static final long STATUS_RECHECK_MS = 10_000;
    /** Minimum spacing between progress writes per transfer; relaying is never throttled. */
    private static final long PROGRESS_WRITE_INTERVAL_MS = 2_000;
    /** Progress-write entries older than this are swept opportunistically. */
    private static final long PROGRESS_ENTRY_MAX_AGE_MS = 10 * 60_000;

    /** What relay() needs per frame, cached per session so we skip the DB on the hot path. */
    private record CachedTransfer(String peerDeviceId, long fileSize, long verifiedAt) {
    }

    private final WsSessionRegistry registry;
    private final PresenceService presenceService;
    private final TransferRepository transferRepository;
    private final TransferService transferService;
    private final ObjectMapper objectMapper;

    /** Last time markProgress was persisted, per transferId. */
    private final ConcurrentHashMap<String, Long> lastProgressWriteAt = new ConcurrentHashMap<>();
    private volatile long lastProgressSweepAt = System.currentTimeMillis();

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
                relay(session, deviceId, type, data);
                return;
            }
            registry.sendToDevice(deviceId, "error", Map.of("message", "Unknown message type: " + type));
        } catch (Exception e) {
            log.warn("Bad WS message from device {}: {}", deviceId, e.getMessage());
            registry.sendToDevice(deviceId, "error", Map.of("message", "Malformed message"));
        }
    }

    private void relay(WebSocketSession session, String fromDeviceId, String type, JsonNode data) {
        String transferId = data.path("transferId").asText("");
        if (transferId.isEmpty()) {
            registry.sendToDevice(fromDeviceId, "error", Map.of("message", "transferId is required"));
            return;
        }
        CachedTransfer cached = authorize(session, fromDeviceId, transferId);
        if (cached == null) {
            return; // authorize() already sent the error frame
        }

        if ("transfer.progress".equals(type)) {
            maybePersistProgress(transferId, data.path("bytesTransferred").asLong(0), cached.fileSize());
        }

        ObjectNode forwarded = (ObjectNode) data.deepCopy();
        forwarded.put("fromDeviceId", fromDeviceId);
        boolean delivered = registry.sendToDevice(cached.peerDeviceId(), type, forwarded);
        if (!delivered) {
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Peer is offline", "transferId", transferId));
        }
    }

    /**
     * Checks that {@code fromDeviceId} may relay frames for {@code transferId},
     * hitting the DB only on the first frame and then at most every
     * {@link #STATUS_RECHECK_MS} per transfer; in between, the cached result in
     * the session attributes is trusted. Transfers are short-lived, so a
     * status change is picked up at the next re-check. Returns null (after
     * sending an error frame) when relaying is not allowed.
     */
    private CachedTransfer authorize(WebSocketSession session, String fromDeviceId, String transferId) {
        Map<String, CachedTransfer> cache = transferCache(session);
        CachedTransfer cached = cache.get(transferId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.verifiedAt() < STATUS_RECHECK_MS) {
            return cached;
        }

        Transfer transfer = transferRepository.findById(transferId).orElse(null);
        if (transfer == null || !transfer.involvesDevice(fromDeviceId)) {
            cache.remove(transferId);
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Unknown transfer or device not part of it", "transferId", transferId));
            return null;
        }
        Transfer.Status status = transfer.getStatus();
        if (status != Transfer.Status.ACCEPTED && status != Transfer.Status.IN_PROGRESS) {
            cache.remove(transferId);
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Transfer is " + status + "; signaling not allowed", "transferId", transferId));
            return null;
        }
        Device peer = transfer.deviceOtherThan(fromDeviceId);
        if (peer == null) {
            registry.sendToDevice(fromDeviceId, "error",
                    Map.of("message", "Peer device not assigned yet", "transferId", transferId));
            return null;
        }
        cached = new CachedTransfer(peer.getId(), transfer.getFileSize(), now);
        cache.put(transferId, cached);
        return cached;
    }

    /**
     * Persists progress at most once every {@link #PROGRESS_WRITE_INTERVAL_MS}
     * per transfer so a chatty sender doesn't turn every frame into a DB
     * write. The final write (bytes {@code >=} fileSize) always goes through.
     */
    private void maybePersistProgress(String transferId, long bytesTransferred, long fileSize) {
        long now = System.currentTimeMillis();
        if (bytesTransferred >= fileSize) {
            lastProgressWriteAt.remove(transferId);
            transferService.markProgress(transferId, bytesTransferred);
            return;
        }
        Long lastWriteAt = lastProgressWriteAt.get(transferId);
        if (lastWriteAt != null && now - lastWriteAt < PROGRESS_WRITE_INTERVAL_MS) {
            return;
        }
        lastProgressWriteAt.put(transferId, now);
        sweepStaleProgressEntries(now);
        transferService.markProgress(transferId, bytesTransferred);
    }

    /** Drops progress-write entries for transfers that stopped reporting (e.g. failed mid-flight). */
    private void sweepStaleProgressEntries(long now) {
        if (now - lastProgressSweepAt < PROGRESS_ENTRY_MAX_AGE_MS) {
            return;
        }
        lastProgressSweepAt = now;
        lastProgressWriteAt.entrySet().removeIf(e -> now - e.getValue() >= PROGRESS_ENTRY_MAX_AGE_MS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, CachedTransfer> transferCache(WebSocketSession session) {
        return (Map<String, CachedTransfer>) session.getAttributes()
                .computeIfAbsent(ATTR_TRANSFER_CACHE, key -> new ConcurrentHashMap<String, CachedTransfer>());
    }

    private String attr(WebSocketSession session, String key) {
        return (String) session.getAttributes().get(key);
    }
}
