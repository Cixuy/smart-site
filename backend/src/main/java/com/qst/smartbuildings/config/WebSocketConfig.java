package com.qst.smartbuildings.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VideoRealtimeWebSocketHandler videoRealtimeWebSocketHandler;
    private final SensorRealtimeWebSocketHandler sensorRealtimeWebSocketHandler;

    public WebSocketConfig(VideoRealtimeWebSocketHandler videoRealtimeWebSocketHandler,
                           SensorRealtimeWebSocketHandler sensorRealtimeWebSocketHandler) {
        this.videoRealtimeWebSocketHandler = videoRealtimeWebSocketHandler;
        this.sensorRealtimeWebSocketHandler = sensorRealtimeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(videoRealtimeWebSocketHandler, "/ws/realtime")
                .setAllowedOriginPatterns("*");
        registry.addHandler(sensorRealtimeWebSocketHandler, "/ws/sensor/subscribe")
                .setAllowedOriginPatterns("*");
    }
}
