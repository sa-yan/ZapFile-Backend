package com.sayan.zapfile.config;

import com.sayan.zapfile.relay.RelayHandler;
import com.sayan.zapfile.signaling.SignalingHandler;
import com.sayan.zapfile.signaling.WsAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;
    private final RelayHandler relayHandler;
    private final WsAuthInterceptor wsAuthInterceptor;

    public WebSocketConfig(SignalingHandler signalingHandler,
                           RelayHandler relayHandler,
                           WsAuthInterceptor wsAuthInterceptor) {
        this.signalingHandler = signalingHandler;
        this.relayHandler = relayHandler;
        this.wsAuthInterceptor = wsAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws")
                .addInterceptors(wsAuthInterceptor)
                .setAllowedOrigins("*"); // mobile clients have no Origin; tighten if a web client is added
        registry.addHandler(relayHandler, "/relay")
                .addInterceptors(wsAuthInterceptor)
                .setAllowedOrigins("*");
    }

    /** Raise per-frame buffers so relay clients can stream in 64 KB chunks. */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
    }
}
