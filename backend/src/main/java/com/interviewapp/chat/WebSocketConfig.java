package com.interviewapp.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PracticeWebSocketHandler practiceWebSocketHandler;

    public WebSocketConfig(PracticeWebSocketHandler practiceWebSocketHandler) {
        this.practiceWebSocketHandler = practiceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(practiceWebSocketHandler, "/ws/sessions/*")
                .setAllowedOriginPatterns("*");
    }

    /** マイク音声のバイナリフレームに備えてバッファ上限を広げる。 */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxSessionIdleTimeout(600_000L);
        return container;
    }
}
