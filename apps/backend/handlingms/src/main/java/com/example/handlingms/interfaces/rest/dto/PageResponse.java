package com.example.handlingms.interfaces.rest.dto;

import java.util.List;

/**
 * ページネーション応答 DTO（handlingms、US15 / IT5 3.x）。
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
