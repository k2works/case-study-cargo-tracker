package com.example.cargotracker.tracking.domain.model.entities;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.ResponseStatus;
import java.time.Instant;

/**
 * 輸送中に起きた例外 1 件（domain-model.md「TrackingException」/ UC16）。
 *
 * <p><b>{@code TrackingActivity} の中のエンティティ</b>で、単独では存在しない。
 * 例外は追跡している貨物に起きるものであり、貨物から切り離すと
 * 「どの貨物の例外か」を別の仕組みで保たなければならなくなる。</p>
 *
 * <p><b>追記のみ</b>（不変条件 6）。解決しても事実は消えず、料金調整の根拠として
 * 残る。消えるのは「対応が要る」という状態だけである。</p>
 *
 * <p><b>緊急かどうかは持たない</b>（不変条件 7）。{@link ExceptionType#urgent()} が
 * 答える——属性にすると、起票した人が「急ぎではない紛失」を作れてしまう。</p>
 *
 * <p>不変（immutable）にしてある。状態を変える操作は新しい値を返すので、
 * 集約はイベントを適用した結果だけを持てばよい。</p>
 */
public record TrackingException(
        String exceptionId,
        ExceptionType type,
        Instant occurredAt,
        // 起きた場所。分からないこともある（船の上で起きた破損）。
        String unLocode,
        String description,
        ResponseStatus responseStatus,
        String resolution,
        Instant resolvedAt) {

    /** 起票する（US19 §受入基準 1）。 */
    public static TrackingException report(String exceptionId, ExceptionType type,
            Instant occurredAt, String unLocode, String description) {
        if (exceptionId == null || exceptionId.isBlank()) {
            throw new BusinessRuleViolation("例外 ID は必須です");
        }
        if (type == null) {
            throw new BusinessRuleViolation("例外種別は必須です");
        }
        if (occurredAt == null) {
            throw new BusinessRuleViolation("発生日時は必須です");
        }
        if (description == null || description.isBlank()) {
            // **何が起きたか読めない記録を残さない。** 起票だけあって理由が
            // 無いと、対応する人は電話で聞き直すところから始める。
            throw new BusinessRuleViolation("発生状況は必須です");
        }
        return new TrackingException(exceptionId, type, occurredAt, unLocode,
                description.trim(), ResponseStatus.REPORTED, null, null);
    }

    /** 対応を始める（US19 §受入基準 4）。 */
    public TrackingException startResponding() {
        requireModifiable();
        return new TrackingException(exceptionId, type, occurredAt, unLocode, description,
                ResponseStatus.RESPONDING, resolution, resolvedAt);
    }

    /**
     * 解決する（US19 §受入基準 4）。
     *
     * <p><b>対応開始を必ず踏ませない。</b> 現場では「連絡したらもう着いていた」が
     * ある。手順のためにボタンを 2 度押させると、記録が実態から遅れる。</p>
     */
    public TrackingException resolve(String resolution, Instant resolvedAt) {
        requireResolvable(resolution);
        return new TrackingException(exceptionId, type, occurredAt, unLocode, description,
                ResponseStatus.RESOLVED, resolution.trim(), resolvedAt);
    }

    /** 決着しているか。一覧（S42）は既定でこれを外す。 */
    public boolean settled() {
        return responseStatus.settled();
    }

    /** 緊急か。<b>種別が答える</b>（不変条件 7）。 */
    public boolean urgent() {
        return type.urgent();
    }

    /**
     * まだ動かせるか（不変条件 6）。
     *
     * <p><b>集約からも呼ぶ。</b> 呼び出し側に「解決済みかどうか」を書き直させない
     * ——判定が 2 つになると、片方だけが正しい形になる。</p>
     */
    public void requireModifiable() {
        if (settled()) {
            // 追記のみ（不変条件 6）。解決した事実を書き換えない。
            throw new BusinessRuleViolation("解決した例外は変更できません");
        }
    }

    /**
     * 解決してよいか（{@link #resolve} と同じ判定）。
     *
     * <p>集約はイベントを出す前にこれで断る。値を作って捨てると、
     * 「検査のために呼んでいる」ことがコードから読めない。</p>
     */
    public void requireResolvable(String resolution) {
        requireModifiable();
        if (resolution == null || resolution.isBlank()) {
            // 何をしたか読めない記録を残さない。
            throw new BusinessRuleViolation("対応内容は必須です");
        }
    }
}
