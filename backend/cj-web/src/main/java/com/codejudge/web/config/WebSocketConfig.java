package com.codejudge.web.config;

import com.codejudge.web.websocket.JudgeWebSocketHandler;
import com.codejudge.web.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：把判题结果推送端点 {@code /ws/judge/{submissionId}} 暴露出来。
 * <p>
 * 握手阶段接入 {@link JwtHandshakeInterceptor} 做 token 鉴权；跨域白名单与前端开发端口对齐
 *（生产由 Nginx 同域转发，不需要这段）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final JudgeWebSocketHandler judgeWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(judgeWebSocketHandler, "/ws/judge/{submissionId}")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173");
    }
}
