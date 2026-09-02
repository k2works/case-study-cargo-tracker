package com.example.bookingms.domain.model.valueobjects;

import java.util.UUID;

/**
 * 見積の識別子（US01・[ADR-028] 決定 7）。
 *
 * <p><strong>UUID である。</strong>推測できないことに意味がある——連番だけにすると、
 * URL を 1 つ増減させて他の荷主の見積が開ける。
 *
 * <p>荷主と電話で読み合わせる番号は {@link EstimateNumber} が別に持つ。
 *
 * @param value 識別子
 */
public record EstimateId(UUID value) {

    public EstimateId {
        if (value == null) {
            throw new IllegalArgumentException("見積の識別子を指定してください");
        }
    }

    public static EstimateId generate() {
        return new EstimateId(UUID.randomUUID());
    }

    /**
     * 文字列から読む。
     *
     * <p><strong>形が違えば断る。</strong>「見つかりません」に化けさせない
     * ——解析の失敗と、存在しない見積は別である。
     */
    public static EstimateId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("見積の識別子を指定してください");
        }
        try {
            return new EstimateId(UUID.fromString(value));
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("見積の識別子の形式が違います: " + value, malformed);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
