package com.codejudge.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 提交记录分页查询参数。
 * <p>
 * 支持两种翻页方式：
 * <ul>
 *     <li><b>offset 分页</b>：传 page / size，适合需要"跳到第 N 页"的场景；</li>
 *     <li><b>游标分页</b>：传 lastTime + lastId（上一页最后一条的两个字段），
 *         适合无限滚动。深分页时 offset 越大越慢（MySQL 要扫掉前 N 条再丢弃），
 *         而游标直接定位到索引位置，耗时与页码无关。</li>
 * </ul>
 * 两个游标字段都传了才走游标模式，否则回退到 offset 模式。
 */
@Data
public class SubmissionQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter CURSOR_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long page = 1L;

    private Long size = 10L;

    /** 按题目筛选，可选 */
    private Long problemId;

    /** 按状态筛选，可选：AC / WA / TLE ... */
    private String status;

    /**
     * 游标：上一页最后一条的提交时间，格式 {@code yyyy-MM-dd HH:mm:ss}。
     * <p>
     * 用「时间 + id」复合游标而不是只用 id：如果按 submit_time 排序，
     * 同一毫秒内可能有两条记录，单靠 id 会漏数据或重复。
     * <p>
     * 这里用 String 而不是 LocalDateTime，是为了让本模块不引入 Spring 的
     * {@code @DateTimeFormat}：GET 参数里的 LocalDateTime 默认只认 ISO-8601
     * （带 {@code T}），而接口返回的时间是空格分隔的，直接绑会失败。
     * 转换放在 {@link #lastTimeAsLocalDateTime()} 里显式做。
     */
    private String lastTime;

    /** 游标：上一页最后一条的 id，与 lastTime 共同构成唯一位置 */
    private Long lastId;

    /** 是否走游标模式 */
    public boolean useCursor() {
        return lastTime != null && !lastTime.isBlank() && lastId != null;
    }

    /**
     * 把游标时间解析成 {@link LocalDateTime}。
     *
     * @return 未使用游标时返回 null
     * @throws IllegalArgumentException 格式不合法（由全局异常处理器转成 400）
     */
    public LocalDateTime lastTimeAsLocalDateTime() {
        if (lastTime == null || lastTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(lastTime.trim(), CURSOR_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("lastTime 格式应为 yyyy-MM-dd HH:mm:ss，实际：" + lastTime);
        }
    }

    public void normalize() {
        if (page == null || page < 1) {
            page = 1L;
        }
        if (size == null || size < 1) {
            size = 10L;
        }
        if (size > 100) {
            size = 100L;
        }
    }
}
