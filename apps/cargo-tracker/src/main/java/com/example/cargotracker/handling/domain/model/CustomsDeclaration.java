package com.example.cargotracker.handling.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 通関申告（US29）。{@link HandlingActivity} の内部エンティティ。
 *
 * <p><strong>集約の内側に置く。</strong> 「通関済でなければ引取できない」という不変条件が
 * 荷役と通関の間にある。別集約にすると、引取を登録する側が通関を読みに行くことになり、
 * <strong>読んだ直後に留置へ変わっても止められない</strong>。
 *
 * <p><strong>通関は業務上の唯一の「止まる仕組み」である。</strong> 誤配も荷受人違いも
 * 「起きた事実」として記録するが、通関前の引き渡しは<strong>実行してはならない</strong>。
 * 記録の対象ではなく、拒否の対象である。
 */
public class CustomsDeclaration {

    /** 永続化されていない申告の ID。 */
    static final long UNSAVED = 0L;

    private final long id;
    private final DeclarationNumber declarationNumber;
    private final Instant declaredAt;

    private CustomsStatus status;
    private Instant clearedAt;

    /**
     * いまの留置が始まった日時。
     *
     * <p><strong>解除して再び留置したら数え直す。</strong> 最初の留置日から数え続けると、
     * 解除して 1 日で再留置した申告がいきなり警告になる。知りたいのは
     * <strong>いま何日止まっているか</strong>である。
     */
    private Instant heldSince;

    /** この呼び出しで積んだ変更履歴（保存されると空になる）。 */
    private final List<CustomsStatusChange> newChanges = new ArrayList<>();

    private CustomsDeclaration(
            long id, DeclarationNumber declarationNumber, Instant declaredAt,
            CustomsStatus status, Instant clearedAt, Instant heldSince) {
        this.id = id;
        this.declarationNumber = declarationNumber;
        this.declaredAt = declaredAt;
        this.status = status;
        this.clearedAt = clearedAt;
        this.heldSince = heldSince;
    }

    /**
     * 新しく申告する。<strong>審査中で始まる。</strong>
     *
     * <p>初期状態を呼び出し側から渡さない。渡せる形にすると、
     * <strong>いきなり通関済の申告を作れてしまう</strong>。
     */
    public static CustomsDeclaration declare(
            DeclarationNumber declarationNumber, Instant declaredAt) {
        if (declarationNumber == null) {
            throw new IllegalArgumentException("申告番号は必須です");
        }
        if (declaredAt == null) {
            throw new IllegalArgumentException("申告日時は必須です");
        }
        return new CustomsDeclaration(
                UNSAVED, declarationNumber, declaredAt, CustomsStatus.PENDING, null, null);
    }

    /**
     * 永続化された値から復元する。
     *
     * <p><strong>ここで整合を求めない。</strong> 列が無かったころに登録された申告は
     * {@code heldSince} を持たない。復元で拒むと、その荷役ごと読めなくなる。
     */
    public static CustomsDeclaration reconstruct(
            long id, DeclarationNumber declarationNumber, Instant declaredAt,
            CustomsStatus status, Instant clearedAt, Instant heldSince) {
        return new CustomsDeclaration(
                id, declarationNumber, declaredAt, status, clearedAt, heldSince);
    }

    /**
     * 状態を更新する（US29「更新時は理由の入力が必須」）。
     *
     * <p><strong>通関済からは戻せない。</strong> 通関が下りたことを根拠に引き渡しが
     * 進むため、後から取り消せる形にすると<strong>引き渡し済みの貨物が
     * 「通関していない」状態になりうる</strong>。誤って通関済にした場合は
     * 取り消し（US36 の系列）で扱う。
     *
     * <p><strong>理由の検査はここに書かない。</strong> {@link CustomsStatusChange} が
     * 持っている。両方に書くと、<strong>片方を壊しても赤にならない</strong> —
     * 検査が 2 か所にあると、どちらが働いているのか確かめられなくなる
     * （IT11 の数え上げで実際に空振りした）。
     *
     * @return 積んだ変更履歴（呼び出し側が通知・自動起票の判断に使う）
     */
    public CustomsStatusChange updateStatus(
            CustomsStatus next, String reason, String changedBy, Instant now) {
        if (next == null) {
            throw new IllegalArgumentException("通関状態は必須です");
        }
        if (status == next) {
            throw new IllegalStateException(
                    "この申告はすでに%sです。同じ状態には更新できません".formatted(next.displayName()));
        }
        if (status == CustomsStatus.CLEARED) {
            throw new IllegalStateException(
                    "通関済の申告は変更できません。"
                            + "誤って登録した場合はシステム管理担当窓口へご連絡ください");
        }
        CustomsStatusChange change =
                new CustomsStatusChange(status, next, reason, changedBy, now);
        this.status = next;
        this.clearedAt = next == CustomsStatus.CLEARED ? now : null;
        // 留置に入るたびに数え直す（解除で消す）
        this.heldSince = next == CustomsStatus.HELD ? now : null;
        newChanges.add(change);
        return change;
    }

    /**
     * 留置のまま既定の日数を超えているか（US29「留置のまま 3 日を超えた申告」）。
     *
     * <p><strong>日付単位で数える。</strong> 経過時間（72 時間）で測ると、
     * 時差の分だけ境界がずれる。日中しか動かさないと気づかない
     * （業務日付は UTC で判断しない）。
     *
     * @param today        業務上の今日
     * @param businessZone 業務のタイムゾーン
     */
    public boolean heldTooLong(LocalDate today, ZoneId businessZone) {
        if (status != CustomsStatus.HELD || heldSince == null || today == null) {
            return false;
        }
        LocalDate since = LocalDate.ofInstant(heldSince, businessZone);
        return since.plusDays(HELD_WARNING_DAYS).isBefore(today);
    }

    /** 留置の警告を出すまでの日数（{@code user_story.md} US29）。 */
    public static final int HELD_WARNING_DAYS = 3;

    /**
     * 引取を許すか（ビジネスルール 2）。
     *
     * <p><strong>判断は状態が持つ。</strong> ここで「CLEARED なら」と書くと、
     * 状態が増えたときに規則が 2 か所に散る。
     */
    public boolean allowsClaim() {
        return status.allowsClaim();
    }

    public boolean isCleared() {
        return status == CustomsStatus.CLEARED;
    }

    /** まだ永続化されていないか。 */
    public boolean isNew() {
        return id == UNSAVED;
    }

    /** この呼び出しで積んだ変更履歴。**保存したら消える。** */
    public List<CustomsStatusChange> newChanges() {
        return List.copyOf(newChanges);
    }

    /** 保存が済んだことを伝える。 */
    public void changesSaved() {
        newChanges.clear();
    }

    public long id() {
        return id;
    }

    public DeclarationNumber declarationNumber() {
        return declarationNumber;
    }

    public Instant declaredAt() {
        return declaredAt;
    }

    public CustomsStatus status() {
        return status;
    }

    public Instant clearedAt() {
        return clearedAt;
    }

    public Instant heldSince() {
        return heldSince;
    }
}
