package com.codejudge.web.websocket;

import com.codejudge.common.context.CurrentUser;
import com.codejudge.dao.mapper.SubmissionMapper;
import com.codejudge.model.entity.Submission;
import com.codejudge.model.judge.JudgeCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 判题结果 WebSocket 推送处理器。
 * <p>
 * 每个 submissionId 维护一组在线会话；判题完成后由 {@link JudgeCompletedEvent} 触发，把结果
 * 实时推送给正在等待的客户端。客户端拿到结果后自行关闭连接。
 * <p>
 * 连接建立时做越权校验：只能订阅"自己的提交"（管理员除外），防止任何人通过猜 submissionId
 * 读取他人代码与判题结果——这是判题系统最基本的安全边界。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeWebSocketHandler extends TextWebSocketHandler {

    private final SubmissionMapper submissionMapper;
    private final ObjectMapper objectMapper;

    /** submissionId -> 该提交上的所有 WebSocket 会话 */
    private final Map<Long, Set<WebSocketSession>> registry = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) {
        Long submissionId = (Long) session.getAttributes().get("submissionId");
        CurrentUser user = (CurrentUser) session.getAttributes().get("user");
        if (submissionId == null || user == null) {
            closeSilently(session);
            return;
        }

        // 越权防护：只能看自己的提交（管理员可看所有人的）
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null
                || (!submission.getUserId().equals(user.id()) && !user.isAdmin())) {
            closeSilently(session);
            return;
        }

        registry.computeIfAbsent(submissionId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("WS 连接建立：submissionId={}, 在线会话数={}", submissionId, registry.get(submissionId).size());
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) {
        remove(session);
    }

    @Override
    public void handleTransportError(@Nonnull WebSocketSession session, @Nonnull Throwable exception) {
        log.warn("WS 传输异常：{}", exception.getMessage());
        remove(session);
    }

    /**
     * 监听判题完成事件，向该提交的所有在线会话推送结果。
     * 与 HTTP 轮询是"或"的关系：客户端连上 WS 就走推送，连不上自动回退到轮询。
     */
    @EventListener
    public void onJudgeCompleted(JudgeCompletedEvent event) {
        Set<WebSocketSession> sessions = registry.get(event.submissionId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException e) {
            log.error("WS 序列化判题结果失败：submissionId={}", event.submissionId(), e);
            return;
        }
        Iterator<WebSocketSession> it = sessions.iterator();
        while (it.hasNext()) {
            WebSocketSession s = it.next();
            try {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(payload));
                } else {
                    it.remove();
                }
            } catch (IOException e) {
                it.remove();
                log.warn("WS 推送失败，移除会话：submissionId={}", event.submissionId());
            }
        }
    }

    private void remove(WebSocketSession session) {
        Long submissionId = (Long) session.getAttributes().get("submissionId");
        if (submissionId == null) {
            return;
        }
        Set<WebSocketSession> set = registry.get(submissionId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                registry.remove(submissionId);
            }
        }
    }

    private void closeSilently(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // 关闭失败忽略，连接已无意义
        }
    }

    @PreDestroy
    public void destroy() {
        registry.values().forEach(set -> set.forEach(this::closeSilently));
        registry.clear();
    }
}
