package com.example.cargotracker.demo;

/**
 * 動作確認用データを作る人。
 *
 * <p><strong>申請した本人は承認できない</strong>（US30）。同じ名前で通すと業務のルールに
 * 弾かれる —— <strong>弾かれること自体が、画面が到達しうる状態だけを作っている証拠</strong>である。
 */
final class DemoActors {

    /** 作業者名。<strong>画面から操作した人と区別が付くようにする。</strong> */
    static final String ACTOR = "demo";

    /** 承認する人。申請者と別人でなければならない。 */
    static final String APPROVER = "demo-tracker";

    private DemoActors() {
    }

    /**
     * <strong>拒まれたことを黙って進まない。</strong>
     *
     * <p>戻り値を捨てると、章が空のままなのに「作った」ことになる。
     * どの手順で止まったかを残す。
     */
    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
