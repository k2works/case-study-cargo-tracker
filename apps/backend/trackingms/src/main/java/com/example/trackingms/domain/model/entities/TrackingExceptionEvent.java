package com.example.trackingms.domain.model.entities;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import java.time.Instant;

/**
 * 起票された例外（US19・US20）。集約 {@link TrackingActivity} の中のエンティティ。
 *
 * <p><strong>解決しても消さない。</strong>実際に起きたことの記録であり、あとから
 * 「無かったこと」にはできない（[ADR-023] 決定 3 と同じ立場）。解決したことを足す。
 *
 * @param id 採番された識別子。起票前は null
 * @param exceptionType 例外の種別
 * @param description 発生状況
 * @param occurredAt 起票した時刻
 * @param resolvedAt 解決した時刻。未解決なら null
 * @param resolutionNotes 対応内容。未解決なら null
 */
public record TrackingExceptionEvent(Long id, ExceptionType exceptionType, String description,
        Instant occurredAt, Instant resolvedAt, String resolutionNotes) {

    /**
     * 検査はここに置く。
     *
     * <p>レコードの正準コンストラクタは公開されるため、名前のある入口にだけ検査を置くと
     * <strong>それを使わなかった一箇所</strong>から素通りできる（返済枠 0.6 と同じ形）。
     */
    public TrackingExceptionEvent {
        if (exceptionType == null) {
            throw new IllegalArgumentException("例外の種別を選んでください");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("発生状況を入力してください");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("発生日時は必須です");
        }
    }

    /** 起票する。<strong>手で起票できる種別かは、種別そのものが答える</strong>。 */
    public static TrackingExceptionEvent raise(ExceptionType exceptionType, String description,
            Instant occurredAt) {
        return new TrackingExceptionEvent(null, exceptionType, description, occurredAt, null, null);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static TrackingExceptionEvent restore(Long id, ExceptionType exceptionType,
            String description, Instant occurredAt, Instant resolvedAt, String resolutionNotes) {
        return new TrackingExceptionEvent(id, exceptionType, description, occurredAt, resolvedAt,
                resolutionNotes);
    }

    /** 未解決か。集約はこれを見て、2 件目の起票を断る。 */
    public boolean unresolved() {
        return resolvedAt == null;
    }

    /** 緊急か。<strong>判定は種別が持つ</strong>（[ADR-024] 決定 3）。 */
    public boolean urgent() {
        return exceptionType.urgent();
    }

    /**
     * 解決する。
     *
     * <p><strong>対応内容を必須にする。</strong>何をしたか分からない解決は、あとから
     * 同じ例外が起きたときに何の役にも立たない。
     */
    public TrackingExceptionEvent resolve(String notes, Instant resolvedAt) {
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("対応内容を入力してください");
        }
        if (!unresolved()) {
            throw new IllegalStateException("この例外はすでに解決しています");
        }
        return new TrackingExceptionEvent(id, exceptionType, description, occurredAt, resolvedAt, notes);
    }
}
