package com.example.bookingms.domain.model;

import java.time.Instant;

/**
 * 誤配が起きた事実（US28・[ADR-026] 決定 3）。
 *
 * <p><strong>状態ではなく、起きたことである。</strong>経路の状況（{@link RoutingStatus}）は
 * 「いまどうなっているか」を表し、再設計すれば {@code ROUTED} へ戻る。
 * <strong>この記録は戻らない</strong>——料金調整の根拠として参照されるため
 * （受入基準 28-8）、解決や再設計で消してはいけない。
 *
 * <p><strong>どこで起きたかを持つ。</strong>「誤配があった」だけでは、荷主にも経理にも
 * 説明できない。予定と違う港がどこだったかまでが事実である。
 *
 * @param at 予定ルート外の荷役が行われた日時
 * @param locationUnLocode その荷役が行われた港
 */
public record Misroute(Instant at, String locationUnLocode) {

    public Misroute {
        if (at == null) {
            throw new IllegalArgumentException("誤配の日時は必須です");
        }
        if (locationUnLocode == null || locationUnLocode.isBlank()) {
            throw new IllegalArgumentException("誤配が起きた港は必須です");
        }
    }

    /**
     * 永続化された行から復元する。<strong>ここでは検査しない</strong>（[ADR-012]）。
     *
     * <p>2 列は独立した nullable として足した（{@code V10__add_misroute.sql}）。片方だけ
     * 入った行があると、コンパクトコンストラクタが例外を投げ<strong>予約詳細が 500 になる</strong>
     * ——不変条件を後から足したときに既存の行が読めなくなるのと同じ形である。守るのは
     * <strong>新しく受け入れるときだけ</strong>でよい。
     *
     * <p>誤配していない予約では両方 {@code null} になる。そのときは記録が無いことを表す
     * {@code null} を返す——空の記録を作ると「誤配したが、いつどこかは分からない」と
     * 区別できない。
     */
    public static Misroute restore(Instant at, String locationUnLocode) {
        return at == null || locationUnLocode == null || locationUnLocode.isBlank()
                ? null
                : new Misroute(at, locationUnLocode);
    }
}
