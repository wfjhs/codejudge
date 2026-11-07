package com.codejudge.web.websocket;

import com.codejudge.common.context.CurrentUser;
import com.codejudge.common.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 握手拦截器。
 * <p>
 * 在握手阶段完成两件事：
 * 1. 从路径 {@code /ws/judge/{id}} 解析出 submissionId，写入属性；
 * 2. 从 query 的 token（或 Authorization 头）校验身份，把 {@link CurrentUser} 写入属性。
 * <p>
 * 鉴权失败直接拒绝握手，避免无效连接进来占用资源。真正的"只能订阅自己提交"的越权校验
 * 放到 {@link JudgeWebSocketHandler#afterConnectionEstablished} 里（握手时还想顺带做一次 DB 归属校验）。
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response,
                                   @Nonnull WebSocketHandler wsHandler, @Nonnull Map<String, Object> attributes) {
        // 1) submissionId：路径末尾的数字段
        Long submissionId = parseSubmissionId(request.getURI().getPath());
        if (submissionId == null) {
            return false;
        }
        attributes.put("submissionId", submissionId);

        // 2) token：优先 query，其次 Authorization 头
        String token = parseToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.isValid(token)) {
            return false;
        }
        try {
            CurrentUser user = jwtUtil.parse(token);
            attributes.put("user", user);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response,
                               @Nonnull WebSocketHandler wsHandler, Exception exception) {
        // 握手成功后的收尾无需处理
    }

    private Long parseSubmissionId(String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx + 1 >= path.length()) {
            return null;
        }
        try {
            return Long.valueOf(path.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (StringUtils.hasText(query)) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        }
        String auth = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring("Bearer ".length());
        }
        return null;
    }
}
