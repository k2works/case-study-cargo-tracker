package com.example.cargotracker.tracking.domain.model.entities;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionOccurrence;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionResolution;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TransportStatus;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Instant;
import java.time.LocalDate;

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
    private ExceptionResolution resolution;

    private TrackingExceptionEvent(
            long id,
            ExceptionOccurrence occurrence,
            boolean escalationFlag,
            TransportStatus statusBefore,
            Instant resolvedAt,
            ExceptionResolution resolution) {
        this.id = id;
        this.occurrence = occurrence;
        this.escalationFlag = escalationFlag;
        this.statusBefore = statusBefore;
        this.resolvedAt = resolvedAt;
        this.resolution = resolution;
    }

    /**
     * 新しく起票する。
     *
     * <p><strong>エスカレーションの要否は種別が決める。</strong> 呼び出し側から
     * フラグを受け取らない — 受け取れば、紛失をエスカレーションせずに起票できる。
     *
     * @param statusBefore 発生直前の輸送状態（**解決時の復帰先**）
     */
    // **分割で公開せざるを得なくなった**（IT19 の C8。ADR-024）。
    // 起票してよいのは集約ルート（TrackingActivity）だけであり、
    // **EntityEncapsulationTest が検査している**（IT20）
    public static TrackingExceptionEvent raise(
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
            ExceptionResolution resolution) {
        return new TrackingExceptionEvent(
                id, occurrence, escalationFlag, statusBefore, resolvedAt, resolution);
    }

    /**
     * 対応が済んだことを記録する。
     *
     * <p><strong>二度は解決できない。</strong> 再解決を許すと、最初の対応日時が
     * 上書きされて「いつ収束したのか」が分からなくなる。
     */
    // **同上。** 解消してよいのは集約ルートだけである（EntityEncapsulationTest が検査する）
    public void resolve(ExceptionResolution resolution, Instant now) {
        if (isResolved()) {
            throw new IllegalStateException(
                    "この例外はすでに %s に解決済みです。同じ例外を二度解決することはできません"
                            .formatted(resolvedAt));
        }
        if (now == null) {
            throw new IllegalArgumentException("解決日時は必須です");
        }
        if (resolution == null) {
            throw new IllegalArgumentException("対応内容は必須です");
        }
        this.resolvedAt = now;
        this.resolution = resolution;
    }

    /**
     * まだ永続化されていないか。
     *
     * <p><strong>「未保存」の判定をインフラ層にリテラルで書かない。</strong>
     * 書くと同じ知識が 2 か所に分かれ、片方だけ残る形になる。
     */
    public boolean isNew() {
        return id == UNSAVED;
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

    /** 対応内容（対応方針と新しい到着予定日）。未解決なら {@code null}。 */
    public ExceptionResolution resolution() {
        return resolution;
    }

    public String resolutionNotes() {
        return resolution == null ? null : resolution.notes();
    }

    /** 対応で決まった新しい到着予定日。無ければ {@code null}。 */
    public LocalDate revisedArrival() {
        return resolution == null ? null : resolution.revisedArrival();
    }
}
