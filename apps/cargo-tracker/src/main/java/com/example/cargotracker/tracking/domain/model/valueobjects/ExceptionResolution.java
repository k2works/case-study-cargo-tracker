package com.example.cargotracker.tracking.domain.model.valueobjects;

import java.time.LocalDate;

/**
 * 例外の対応内容（US19 の受入基準「対応内容（新しい到着予定日・対応方針）」）。
 *
 * <p><strong>受入基準は括弧の中で 2 つを求めている。</strong> IT10 は
 * 「対応内容を入力して報告を送信できる」という<strong>動作</strong>だけを満たし、
 * 新しい到着予定日を自由記述の中に埋もれさせた（C18）。荷主が遅延でいちばん
 * 知りたいのは<strong>結局いつ着くのか</strong>であり、文章の中にあると
 * 追跡照会の到着予定と食い違う。
 *
 * <p>ふたつをひと組で持つのは、片方だけ渡す呼び出しを型の上で作らせないためである
 * （{@link ExceptionOccurrence} と同じ判断）。
 *
 * @param notes          対応方針。<strong>新しく記録するときは必須</strong>
 * @param revisedArrival 新しい到着予定日。<strong>任意</strong>
 */
public record ExceptionResolution(String notes, LocalDate revisedArrival) {

    /**
     * 新しく対応を記録するときの内容。<strong>対応方針を必須にする。</strong>
     *
     * <p>空の対応報告を荷主に送らない。「対応しました」だけの通知は、
     * 何が起きてどうなったのかを何も伝えない。
     *
     * @param today 業務の暦の上の今日。<strong>過去の到着予定日は記録できない</strong>（C37）
     */
    public static ExceptionResolution report(
            String notes, LocalDate revisedArrival, LocalDate today) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("対応内容は必須です");
        }
        // **新しい到着予定日はこれからの見込みである**（C37）。申告日時（C36）とは
        // 拒む向きが逆になる。追跡照会の到着予定はここから差し替わるため、過去日を
        // 通すと**着いていない貨物に「昨日着く予定」と表示される**。
        // **当日は通す** — 当日入港はありうる
        if (revisedArrival != null && today != null && revisedArrival.isBefore(today)) {
            throw new IllegalArgumentException(
                    "新しい到着予定日に過去の日付は指定できません: " + revisedArrival);
        }
        return new ExceptionResolution(notes, revisedArrival);
    }

    /**
     * 永続化された値から復元する。
     *
     * <p><strong>検査を正準コンストラクタに置かない。</strong> 置くと復元経路も通り、
     * <strong>列が無かったころに解決された例外を読み戻せなくなる</strong>。
     * 落ちるのは例外 1 件ではなく集約全体であり、その貨物の画面ごと 500 になる。
     * V23 が「読み戻す側は NULL を拒んではならない」と書いた意味はこれである。
     *
     * <p>IT10 で {@code ExceptionOccurrence} に同じ形の欠陥を作り、
     * レビューで見つかった。<strong>守りが緩いのではなく、守る時点が違う。</strong>
     */
    public static ExceptionResolution reconstruct(String notes, LocalDate revisedArrival) {
        return new ExceptionResolution(notes, revisedArrival);
    }

    /** 到着予定を差し替えるか。 */
    public boolean hasRevisedArrival() {
        return revisedArrival != null;
    }
}
