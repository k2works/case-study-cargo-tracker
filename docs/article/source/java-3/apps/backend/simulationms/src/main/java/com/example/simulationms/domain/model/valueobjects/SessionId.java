package com.example.simulationms.domain.model.valueobjects;

/**
 * 継続実行の識別子（{@code SES-YYYYMMDD-NNNN}）。
 *
 * <p>実行 ID と同じく、<strong>採番を裁くのは一意制約である</strong>——
 * 数えてから書くまでの間に別のセッションが入り込む。
 */
public record SessionId(String value) {

    public SessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("セッション ID は必須です");
        }
    }

    public static SessionId of(String value) {
        return new SessionId(value);
    }
}
