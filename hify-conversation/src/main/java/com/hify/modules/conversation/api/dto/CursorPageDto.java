package com.hify.modules.conversation.api.dto;

import java.util.List;

/**
 * 游标分页结果（禁止 LIMIT offset 深分页）。
 *
 * @param records    本页记录，按 id 倒序
 * @param nextCursor 下一页游标（本页最末一条记录的 id）；没有更多时为 null
 * @param <T>        记录类型
 */
public record CursorPageDto<T>(
        List<T> records,
        Long nextCursor
) {

    public static <T> CursorPageDto<T> of(List<T> records, Long nextCursor) {
        return new CursorPageDto<>(records, nextCursor);
    }
}
