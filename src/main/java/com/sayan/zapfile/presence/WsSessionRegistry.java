package com.sayan.zapfile.presence;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Tracks which devices currently hold an open WebSocket connection and
 * provides the only way to push a message to a device or to all of a
 * user's devices. File bytes never pass through here — only small JSON
 * control/signaling messages.
 */
@Component
public class WsSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsSessionRegistry.class);

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessionsByDeviceId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> deviceIdsByUserId = new ConcurrentHashMap<>();

    public WsSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String userId, String deviceId, WebSocketSession session) {
        WebSocketSession previous = sessionsByDeviceId.put(deviceId, session);
        if (previous != null && previous.isOpen()) {
            try {
                previous.close();
            } catch (IOException ignored) {
            }
        }
        deviceIdsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(deviceId);
    }

    public void unregister(String userId, String deviceId, WebSocketSession session) {
        // only remove if this session is still the registered one (a reconnect may have
        // replaced it). Compare by id: the registered session is a decorator around the raw one.
        WebSocketSession current = sessionsByDeviceId.get(deviceId);
        if (current != null && current.getId().equals(session.getId())) {
            sessionsByDeviceId.remove(deviceId, current);
        }
        deviceIdsByUserId.computeIfPresent(userId, (k, devices) -> {
            if (!sessionsByDeviceId.containsKey(deviceId)) {
                devices.remove(deviceId);
            }
            return devices.isEmpty() ? null : devices;
        });
    }

    public boolean isDeviceOnline(String deviceId) {
        WebSocketSession session = sessionsByDeviceId.get(deviceId);
        return session != null && session.isOpen();
    }

    public boolean isUserOnline(String userId) {
        return deviceIdsByUserId.getOrDefault(userId, Set.of()).stream().anyMatch(this::isDeviceOnline);
    }

    public Set<String> onlineDeviceIds(String userId) {
        return Set.copyOf(deviceIdsByUserId.getOrDefault(userId, Set.of()));
    }

    /** Returns true if the message was delivered to an open session. */
    public boolean sendToDevice(String deviceId, String type, Object data) {
        WebSocketSession session = sessionsByDeviceId.get(deviceId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException e) {
            log.warn("Failed to send '{}' to device {}: {}", type, deviceId, e.getMessage());
            return false;
        }
    }

    /** Sends to every online device of the user. Returns true if at least one delivery succeeded. */
    public boolean sendToUser(String userId, String type, Object data) {
        boolean delivered = false;
        for (String deviceId : onlineDeviceIds(userId)) {
            delivered |= sendToDevice(deviceId, type, data);
        }
        return delivered;
    }
}
