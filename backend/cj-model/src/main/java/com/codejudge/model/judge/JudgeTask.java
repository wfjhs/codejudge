package com.codejudge.model.judge;

import java.io.Serial;
import java.io.Serializable;

/**
 * 判题任务（队列消息体）。
 * <p>
 * 只传 submissionId 其实就够了，消费者自己去查库即可。这里额外带上 problemId 和 language，
 * 是为了让消费者能提前过滤掉明显无效的任务（比如题目已被删除），少查一次库。
 * <p>
 * <b>后端不在这里传测试用例</b>：用例必须由判题服务自己从库里读，
 * 客户端传什么都不信 —— 否则用户伪造一份用例就能自己判自己 AC。
 */
public class JudgeTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long submissionId;

    private Long problemId;

    private Long userId;

    private String language;

    /**
     * 入队时间戳（毫秒）。
     * <p>
     * 用途：任务被取出后会被移动到 processing 列表，如果进程崩溃，任务就留在那里。
     * 恢复线程靠这个时间戳判断"这个任务是不是已经超时了"，超时才重新投递，
     * 避免把正在正常处理的任务重复投递（虽然幂等能兜住，但会浪费判题算力）。
     */
    private Long enqueueTime;

    public JudgeTask() {
    }

    public JudgeTask(Long submissionId, Long problemId, Long userId, String language) {
        this.submissionId = submissionId;
        this.problemId = problemId;
        this.userId = userId;
        this.language = language;
        this.enqueueTime = System.currentTimeMillis();
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Long getEnqueueTime() {
        return enqueueTime;
    }

    public void setEnqueueTime(Long enqueueTime) {
        this.enqueueTime = enqueueTime;
    }

    @Override
    public String toString() {
        return "JudgeTask{submissionId=" + submissionId + ", problemId=" + problemId
                + ", userId=" + userId + ", language='" + language + "'}";
    }
}
