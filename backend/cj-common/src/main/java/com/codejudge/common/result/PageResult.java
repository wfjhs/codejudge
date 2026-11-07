package com.codejudge.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。
 * <p>
 * 刻意不直接返回 MyBatis-Plus 的 {@code IPage}：
 * <ul>
 *     <li>IPage 里有一堆前端用不到的字段（searchCount、optimizeCountSql、countId 等），
 *         直接返回会让接口文档变脏，也暴露了 ORM 的实现细节</li>
 *     <li>cj-common 不依赖 MyBatis-Plus，保持这个模块的纯净</li>
 * </ul>
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long page;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    /**
     * 游标分页是否还有下一页。
     * <p>
     * offset 分页时为 {@code null}（用 pages 判断）；游标模式下 total / pages 无意义，
     * 前端只看这个字段——因为游标分页刻意不查总数（深分页时 COUNT 全表本身就是瓶颈）。
     */
    private Boolean hasMore;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(Collections.emptyList(), 0, page, size);
    }

    /**
     * 游标分页结果。
     * <p>
     * total 与 pages 置为 -1 而不是 0，语义上表示"未知"而非"没有数据"——
     * 避免前端把 0 当成空列表的判据。
     *
     * @param hasMore 是否还有下一页（由调用方多取一条来判断）
     */
    public static <T> PageResult<T> cursor(List<T> records, long size, boolean hasMore) {
        PageResult<T> r = new PageResult<>();
        r.records = records == null ? Collections.emptyList() : records;
        r.total = -1;
        r.page = 0;
        r.size = size;
        r.pages = -1;
        r.hasMore = hasMore;
        return r;
    }
}
