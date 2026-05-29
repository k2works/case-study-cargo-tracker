package com.example.trackingms.interfaces.rest.dto;

import java.util.List;

/**
 * ページネーション応答 DTO（trackingms、US17 / IT5 2.4）。
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
