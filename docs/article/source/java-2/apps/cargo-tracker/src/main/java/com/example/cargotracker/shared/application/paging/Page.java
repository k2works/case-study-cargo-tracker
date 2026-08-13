package com.example.cargotracker.shared.application.paging;

import java.util.List;

/**
 * ページ送りされた一覧。
 *
 * <p>同じ計算（総ページ数・前後の有無）を一覧ごとに書くと必ずずれる。
 * **境界（最終ページ・0 件・範囲外のページ番号）は、たまにしか出ないが出ると壊れる。**
 *
 * @param items      このページの中身
 * @param request    要求したページ
 * @param totalItems 総件数
 * @param <T>        中身の型
 */
public record Page<T>(List<T> items, PageRequest request, long totalItems) {

    public Page {
        if (totalItems < 0) {
            throw new IllegalArgumentException("総件数は 0 以上です: " + totalItems);
        }
        items = List.copyOf(items);
    }

    public static <T> Page<T> of(List<T> items, PageRequest request, long totalItems) {
        return new Page<>(items, request, totalItems);
    }

    /** 総ページ数。**0 件でも 1 とする**（「0 ページ目」を画面に出さない）。 */
    public int totalPages() {
        if (totalItems == 0) {
            return 1;
        }
        return (int) ((totalItems + PageRequest.SIZE - 1) / PageRequest.SIZE);
    }

    public int pageNumber() {
        return request.pageNumber();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean hasPrevious() {
        return pageNumber() > 1;
    }

    /** 次のページがあるか。**最終ページで「次へ」が押せると空のページに飛ぶ。** */
    public boolean hasNext() {
        return pageNumber() < totalPages();
    }
}
