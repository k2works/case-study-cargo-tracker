package com.example.bookingms.interfaces.rest.dto;

import java.util.List;

/**
 * ページネーション応答 DTO（IT2 ページネーション追加）。
 *
 * <p>一覧 API で利用する汎用ページレスポンス。
 * Offset / Limit 型ページネーションに対応する。</p>
 *
 * @param items 当ページの要素列
 * @param totalCount 全件数
 * @param page 0 始まりのページ番号
 * @param size 1 ページあたりの最大件数
 * @param <T> 要素型
 */
public record PageResponse<T>(
        List<T> items,
        long totalCount,
        int page,
        int size
) {
    public static <T> PageResponse<T> of(List<T> items, long totalCount, int page, int size) {
        return new PageResponse<>(items, totalCount, page, size);
    }
}
