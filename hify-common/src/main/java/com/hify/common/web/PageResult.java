package com.hify.common.web;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结构：在 Result 的 code / message / data 之外，
 * 额外携带 total（总条数）、page（当前页码，从 1 起）、size（每页条数）。
 * data 固定为本页记录列表。
 *
 * @param <T> 记录类型
 */
@Getter
public class PageResult<T> extends Result<List<T>> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long total;
    private final long page;
    private final long size;

    private PageResult(List<T> data, long total, long page, long size) {
        super(SUCCESS_CODE, SUCCESS_MESSAGE, data);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 构造分页成功响应。
     *
     * @param records 本页记录
     * @param total   总条数
     * @param page    当前页码（从 1 起）
     * @param size    每页条数
     */
    public static <T> PageResult<T> ok(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }
}
