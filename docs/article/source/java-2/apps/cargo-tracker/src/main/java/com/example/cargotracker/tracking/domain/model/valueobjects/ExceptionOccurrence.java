package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;

/**
 * 例外の発生状況（US19 / US20）。
 *
 * <p>受入基準はこの 4 つを<strong>ひとまとまりで</strong>要求している
 * （「例外種別・発生状況（場所・日時・理由）を記録できる」）。個別の引数として
 * 持ち回ると、<strong>場所を渡し忘れた呼び出しが型の上では成立してしまう。</strong>
 *
 * <p>必須の検査はここが持つ。集約が起票のたびに同じ検査を書くと、
 * 入口が増えたときに片方だけ緩む（{@code CargoSpecification.of} で起きたのと同じ形）。
 *
 * @param type        例外種別
 * @param location    発生場所
 * @param occurredAt  発生日時
 * @param description 理由・状況。任意（詳細が後から分かることがある）
 */
public record ExceptionOccurrence(
        ExceptionType type, Location location, Instant occurredAt, String description) {

    public ExceptionOccurrence {
        if (type == null) {
            throw new IllegalArgumentException("例外種別は必須です");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("発生日時は必須です");
        }
    }

    /**
     * 新しく起票するときの発生状況。<strong>発生場所を必須にする。</strong>
     *
     * <p><strong>検査を正準コンストラクタに置かない。</strong> 置くと復元経路も通り、
     * <strong>場所の列が無かったころに起票された例外を読み戻せなくなる</strong>。
     * 落ちるのは例外 1 件ではなく集約全体であり、その貨物の画面ごと 500 になる。
     * V22 が「既存行のために NULL 可のままにする」と書いた意味は、
     * <strong>読み戻す側が拒まないこと</strong>まで含む。
     *
     * <p>{@code CargoSpecification} の {@code create} / {@code reconstruct} と同じ判断である
     * （守る時点が違うのであって、守りが緩いのではない）。
     */
    public static ExceptionOccurrence raise(
            ExceptionType type, Location location, Instant occurredAt, String description) {
        if (location == null) {
            throw new IllegalArgumentException("発生場所は必須です");
        }
        return new ExceptionOccurrence(type, location, occurredAt, description);
    }
}
