package com.sayan.zapfile.signaling;

import com.sayan.zapfile.auth.JwtService;
import com.sayan.zapfile.device.DeviceRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the WebSocket handshake: {@code /ws?token=<accessToken>&deviceId=<id>}.
 * Query parameters are used because React Native's WebSocket API cannot reliably
 * set an Authorization header. On success, userId and deviceId are stored as
 * session attributes for the handler.
 */
@Component
public class WsAuthInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_DEVICE_ID = "deviceId";

    private final JwtService jwtService;
    private final DeviceRepository deviceRepository;

    public WsAuthInterceptor(JwtService jwtService, DeviceRepository deviceRepository) {
        this.jwtService = jwtService;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MultiValueMap<String, String> params =
                UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String token = params.getFirst("token");
        String deviceId = params.getFirst("deviceId");
        if (token == null || deviceId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Optional<String> userId = jwtService.extractUserId(token, JwtService.TYPE_ACCESS);
        if (userId.isEmpty() || deviceRepository.findByIdAndUserId(deviceId, userId.get()).isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USER_ID, userId.get());
        attributes.put(ATTR_DEVICE_ID, deviceId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
