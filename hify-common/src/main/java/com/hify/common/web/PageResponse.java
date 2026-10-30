package com.hify.common.web;

import java.util.List;

/**
 * 分页数据载荷，作为 {@link Result} 的 data。
 * 字段与前端 HifyTable 的 PagedData 契约对齐（records/total/page/size）。
 *
 * @param records 本页记录
 * @param total   总条数
 * @param page    当前页码（从 1 起）
 * @param size    每页条数
 */
public record PageResponse<T>(
        List<T> records,
        long total,
        long page,
        long size
) {

    public static <T> PageResponse<T> of(List<T> records, long total, long page, long size) {
        return new PageResponse<>(records, total, page, size);
    }
}
