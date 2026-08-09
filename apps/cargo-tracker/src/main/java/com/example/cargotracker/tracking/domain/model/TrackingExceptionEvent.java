package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;

/**
 * 例外の発生と解決の記録（US19 / US20）。{@link TrackingActivity} の内部エンティティ。
 *
 * <p><strong>発生前の輸送状態を自分で持つ。</strong> 解決したときに戻る先を
 * 荷役イベントの履歴から導き直すと、ユニットテストが緑でも
 * <strong>リクエストをまたいだときに誤った状態へ復帰する</strong>
 * （{@code data-model.md} の {@code status_before} の注記）。
 *
 * <p><strong>解決は「無かったこと」ではない。</strong> 破損した貨物は解決後も
 * 破損したままであり、その事実は US21 の料金調整の根拠になる。
 * 解決が意味するのは<strong>対応が済んだこと</strong>だけである。
 */
public class TrackingExceptionEvent {

    /** 永続化されていない例外の ID。 */
    static final long UNSAVED = 0L;

    private final long id;
    private final ExceptionOccurrence occurrence;
    private final boolean escalationFlag;
    private final TransportStatus statusBefore;

    private Instant resolvedAt;
    private String resolutionNotes;

    private TrackingExceptionEvent(
            long id,
            ExceptionOccurrence occurrence,
            boolean escalationFlag,
            TransportStatus statusBefore,
            Instant resolvedAt,
            String resolutionNotes) {
        this.id = id;
        this.occurrence = occurrence;
        this.escalationFlag = escalationFlag;
        this.statusBefore = statusBefore;
        this.resolvedAt = resolvedAt;
        this.resolutionNotes = resolutionNotes;
    }

    /**
     * 新しく起票する。
     *
     * <p><strong>エスカレーションの要否は種別が決める。</strong> 呼び出し側から
     * フラグを受け取らない — 受け取れば、紛失をエスカレーションせずに起票できる。
     *
     * @param statusBefore 発生直前の輸送状態（**解決時の復帰先**）
     */
    static TrackingExceptionEvent raise(
            ExceptionOccurrence occurrence, TransportStatus statusBefore) {
        if (occurrence == null) {
            throw new IllegalArgumentException("発生状況は必須です");
        }
        if (statusBefore == null) {
            throw new IllegalArgumentException("発生前の輸送状態は必須です");
        }
        return new TrackingExceptionEvent(
                UNSAVED, occurrence, occurrence.type().escalationRequired(),
                statusBefore, null, null);
    }

    /**
     * 永続化された値から復元する。
     *
     * <p><strong>エスカレーションのフラグは保存された値をそのまま使う。</strong>
     * 種別から導き直すと、規則を変えた日に過去の記録の意味が書き換わる。
     * 「そのとき何をしたか」は記録であって、いま計算し直すものではない。
     */
    public static TrackingExceptionEvent reconstruct(
            long id,
            ExceptionOccurrence occurrence,
            boolean escalationFlag,
            TransportStatus statusBefore,
            Instant resolvedAt,
            String resolutionNotes) {
        return new TrackingExceptionEvent(
                id, occurrence, escalationFlag, statusBefore, resolvedAt, resolutionNotes);
    }

    /**
     * 対応が済んだことを記録する。
     *
     * <p><strong>二度は解決できない。</strong> 再解決を許すと、最初の対応日時が
     * 上書きされて「いつ収束したのか」が分からなくなる。
     */
    void resolve(String notes, Instant now) {
        if (isResolved()) {
            throw new IllegalStateException(
                    "この例外はすでに %s に解決済みです。同じ例外を二度解決することはできません"
                            .formatted(resolvedAt));
        }
        if (now == null) {
            throw new IllegalArgumentException("解決日時は必須です");
        }
        this.resolvedAt = now;
        this.resolutionNotes = notes;
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    public long id() {
        return id;
    }

    /** 発生状況（種別・場所・日時・理由）。 */
    public ExceptionOccurrence occurrence() {
        return occurrence;
    }

    public ExceptionType exceptionType() {
        return occurrence.type();
    }

    public Location location() {
        return occurrence.location();
    }

    public Instant occurredAt() {
        return occurrence.occurredAt();
    }

    public String description() {
        return occurrence.description();
    }

    public boolean escalationFlag() {
        return escalationFlag;
    }

    /** 発生直前の輸送状態。**解決したらここへ戻す。** */
    public TransportStatus statusBefore() {
        return statusBefore;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public String resolutionNotes() {
        return resolutionNotes;
    }
}
