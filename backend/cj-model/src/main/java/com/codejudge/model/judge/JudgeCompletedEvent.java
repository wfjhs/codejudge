package com.codejudge.model.judge;

import com.codejudge.model.enums.SubmissionStatus;

/**
 * 判题完成事件（纯 POJO，不依赖 Spring）。
 * <p>
 * cj-service 在结果回写 DB 之后发布，cj-web 的 WebSocket 层监听并实时推送给前端。
 * <p>
 * 用 Spring 应用事件解耦：事件本身只是个普通对象，放在 cj-model，让 cj-service（发布方）
 * 和 cj-web（监听方）都能引用，且不破坏 cj-web → cj-service 的单向依赖关系。
 * 如果以后把判题消费者拆成独立进程/服务，把这个事件换成 MQ 消息即可，其它代码不用动。
 */
public record JudgeCompletedEvent(
        Long submissionId,
        SubmissionStatus status,
        int score,
        Integer timeCost,
        Integer memoryCost,
        String errorMsg,
        Integer failCase
) {
}
