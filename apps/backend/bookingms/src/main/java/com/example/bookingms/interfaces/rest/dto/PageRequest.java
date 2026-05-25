package com.example.bookingms.interfaces.rest.dto;

/**
 * ページネーション要求パラメータ（IT2 / ADR-0008）。
 *
 * <p>サニタイズ規約を 1 箇所に集約する値オブジェクト。
 * Controller でクエリパラメータから生成し、Service / Mapper へ
 * すでにサニタイズ済みの値を渡す。</p>
 *
 * <ul>
 *   <li>{@code page < 0} → 0 に切り上げ</li>
 *   <li>{@code size <= 0} → {@link #DEFAULT_PAGE_SIZE} (20)</li>
 *   <li>{@code size > MAX_PAGE_SIZE} → {@link #MAX_PAGE_SIZE} (200) に切り下げ</li>
 * </ul>
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 200;

    public PageRequest {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    public int offset() {
        return page * size;
    }
}
