package com.example.cargotracker.billing.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 精算書の発行（US23）。発行日時と支払期限のひと組。
 *
 * <p><strong>支払期限は発行時に決めて保持する。</strong> 都度計算すると、
 * 期限の設定を変えた日に<strong>既発行分の期限が一斉に動く</strong>
 * （IT13 で税率を請求書ごとに持たせたのとまったく同じ形である）。
 * 荷主に「30 日後まで」と伝えた後で期限が縮むことがあってはならない。
 *
 * <p><strong>期限は日付で判断する。</strong> 時刻付きで比べると、
 * 期限当日の入金が「1 秒過ぎている」で督促対象になる。
 *
 * @param issuedAt 発行日時
 * @param dueDate  支払期限（<strong>この日を含む</strong>）
 */
public record Issuance(Instant issuedAt, LocalDate dueDate) {

    public Issuance {
        if (issuedAt == null) {
            throw new IllegalArgumentException("発行日時は必須です");
        }
        if (dueDate == null) {
            throw new IllegalArgumentException("支払期限は必須です");
        }
    }

    /**
     * 支払期限を過ぎているか。
     *
     * <p><strong>期限当日は過ぎていない。</strong> 「◯日まで」と伝えた当日に
     * 督促が飛ぶと、支払う側は約束を守っているのに催促を受ける。
     *
     * @param today 業務のタイムゾーンでの今日
     */
    public boolean isOverdue(LocalDate today) {
        return today != null && today.isAfter(dueDate);
    }

    /**
     * 何日過ぎているか（<strong>過ぎていなければ 0</strong>）。
     *
     * <p>督促の文面は「何日遅れているか」で変わる。画面が引き算を書き直すと、
     * 期限当日の扱いが場所ごとにずれる。
     */
    public long daysOverdue(LocalDate today) {
        if (!isOverdue(today)) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, today);
    }
}
