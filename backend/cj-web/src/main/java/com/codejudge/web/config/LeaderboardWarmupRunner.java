package com.codejudge.web.config;

import com.codejudge.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时对齐排行榜缓存。
 * <p>
 * 放在 Web 层而不是 Service 层，是因为「启动时机」属于应用装配的职责，
 * 与排行榜本身的业务逻辑无关。Service 只暴露 {@code rebuild()}，
 * 谁来触发、什么时候触发由外层决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardWarmupRunner implements ApplicationRunner {

    private final LeaderboardService leaderboardService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            leaderboardService.rebuild();
        } catch (Exception e) {
            // 重建失败不该阻塞启动：排行榜是派生数据，坏掉只影响榜单展示，
            // 提交判题主链路依然可用。宁可降级，也不要因为一个展示页起不来服务。
            log.warn("排行榜重建失败，榜单可能为空", e);
        }
    }
}
