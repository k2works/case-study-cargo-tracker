package com.example.cargotracker.shared.application.paging;

/**
 * ページ送りの要求。
 *
 * <p>1 ページの件数は {@code ui_design.md} が 20 件と定めている。**画面ごとに変えない。**
 * 一覧によって件数が違うと、利用者は「次へ」を押すたびに何件進んだのか分からなくなる。
 *
 * <p>ページ番号は<strong>利用者から見た 1 始まり</strong>である。URL に現れる値であり、
 * 0 始まりにすると「1 ページ目を見たいのに ?page=0」という説明のつかない URL になる。
 *
 * @param pageNumber ページ番号（1 始まり）
 */
public record PageRequest(int pageNumber) {

    /** 1 ページの件数（{@code ui_design.md}）。 */
    public static final int SIZE = 20;

    /**
     * ページ番号から生成する。
     *
     * <p><strong>範囲外は 1 ページ目として扱う。</strong> URL を直接編集しただけで
     * 500 やマイナスのオフセットにしない。
     *
     * @param pageNumber ページ番号。{@code null} または 1 未満なら 1 ページ目
     */
    public static PageRequest of(Integer pageNumber) {
        return new PageRequest(pageNumber == null || pageNumber < 1 ? 1 : pageNumber);
    }

    /** SQL の OFFSET に渡す値。 */
    public int offset() {
        return (pageNumber - 1) * SIZE;
    }

    /** SQL の LIMIT に渡す値。 */
    public int limit() {
        return SIZE;
    }
}
