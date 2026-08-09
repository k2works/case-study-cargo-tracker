package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
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
        if (location == null) {
            throw new IllegalArgumentException("発生場所は必須です");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("発生日時は必須です");
        }
    }
}
