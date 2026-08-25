package com.example.handlingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 通関申告（US29・UC21・[ADR-025]）。
 *
 * <p>ここで確かめるのは<strong>引取を許してよいか</strong>である。通関が下りていない
 * 貨物を引き渡すと、税関との関係で会社が責任を負う。
 */
@DisplayName("通関申告")
class CustomsDeclarationTest {

    private static final HandlingTrackingNumber TRACKING =
            HandlingTrackingNumber.of("TRK-20260823-0001");
    private static final CargoBookingId BOOKING = CargoBookingId.of("BKG-2026000001");
    private static final DeclarationNumber NUMBER = DeclarationNumber.of("DEC-0001");
    private static final Instant DECLARED_AT = Instant.parse("2027-09-02T00:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static CustomsDeclaration declared() {
        return CustomsDeclaration.declare(NUMBER, BOOKING, TRACKING, DECLARED_AT);
    }

    @Nested
    @DisplayName("申告するとき（US29-1）")
    class WhenDeclaring {

        /** **初期状態は審査中。** 登録の時点で通関済を選べると、ガードが最初から素通りになる。 */
        @Test
        @DisplayName("申告した直後は審査中である")
        void startsPending() {
            assertThat(declared().status()).isEqualTo(CustomsStatus.PENDING);
            assertThat(declared().isCleared()).isFalse();
        }

        /** 登録も履歴に残す。**何も無い状態からは始まらない**（`from_status` も NOT NULL）。 */
        @Test
        @DisplayName("登録そのものが履歴の 1 行目として残る")
        void recordsTheDeclarationItself() {
            List<CustomsStatusChange> history = declared().history();

            assertThat(history).hasSize(1);
            assertThat(history.getFirst().fromStatus()).isEqualTo(CustomsStatus.PENDING);
            assertThat(history.getFirst().toStatus()).isEqualTo(CustomsStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("状態を更新するとき（US29-2）")
    class WhenUpdatingStatus {

        /** **理由は必須。** 空で通すと、監査の履歴が「誰かが変えた」だけになる。 */
        @Test
        @DisplayName("理由なしの更新は断る")
        void requiresAReason() {
            CustomsDeclaration declaration = declared();

            assertThatThrownBy(() ->
                    declaration.updateStatus(CustomsStatus.HELD, "tracker01", " ", DECLARED_AT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("理由");
        }

        @Test
        @DisplayName("更新すると、履歴に日時・変更者・理由が残る")
        void keepsWhoChangedItAndWhy() {
            CustomsDeclaration held = declared()
                    .updateStatus(CustomsStatus.HELD, "tracker01", "書類不備", DECLARED_AT);

            CustomsStatusChange last = held.history().getLast();
            assertThat(last.fromStatus()).isEqualTo(CustomsStatus.PENDING);
            assertThat(last.toStatus()).isEqualTo(CustomsStatus.HELD);
            assertThat(last.changedBy()).isEqualTo("tracker01");
            assertThat(last.reason()).isEqualTo("書類不備");
        }

        /**
         * <strong>{@code isCleared()} は CLEARED のときだけ真。</strong>
         *
         * <p>「PENDING でなければ通す」形にすると、留置・不可の貨物まで引き取れる。
         * ガードは<strong>通してよい 1 つ</strong>を見る。
         */
        @Test
        @DisplayName("通関済のときだけ引き取れる")
        void allowsClaimOnlyWhenCleared() {
            assertThat(declared().isCleared()).isFalse();
            assertThat(declared().updateStatus(CustomsStatus.HELD, "t", "書類不備", DECLARED_AT)
                    .isCleared()).isFalse();
            assertThat(declared().updateStatus(CustomsStatus.REJECTED, "t", "不備", DECLARED_AT)
                    .isCleared()).isFalse();
            assertThat(declared().updateStatus(CustomsStatus.CLEARED, "t", "通関完了", DECLARED_AT)
                    .isCleared()).isTrue();
        }

        /** 通関済になった日時を残す。荷主への説明に要る。 */
        @Test
        @DisplayName("通関済になると、その日時が残る")
        void remembersWhenItCleared() {
            CustomsDeclaration cleared = declared()
                    .updateStatus(CustomsStatus.CLEARED, "t", "通関完了", DECLARED_AT);

            assertThat(cleared.clearedAt()).contains(DECLARED_AT);
        }

        /** 同じ状態への更新は、履歴を無意味に増やす。 */
        @Test
        @DisplayName("いまと同じ状態には更新できない")
        void rejectsUpdatingToTheSameStatus() {
            CustomsDeclaration declaration = declared();

            assertThatThrownBy(() ->
                    declaration.updateStatus(CustomsStatus.PENDING, "t", "変更なし", DECLARED_AT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("決着しているか（[ADR-025] 決定 7）")
    class WhenCheckingSettlement {

        /**
         * <strong>未決着が高々 1 件であることを、登録側が使う。</strong>
         *
         * <p>「最新の 1 件」を暗黙に選ぶ実装にしない。同時に 2 件が審査中だと、
         * ガードの「最新」が何を指すか決まらない。
         */
        @Test
        @DisplayName("審査中と留置は決着していない")
        void pendingAndHeldAreUnsettled() {
            assertThat(declared().isSettled()).isFalse();
            assertThat(declared().updateStatus(CustomsStatus.HELD, "t", "書類不備", DECLARED_AT)
                    .isSettled()).isFalse();
        }

        @Test
        @DisplayName("通関済と不可は決着している")
        void clearedAndRejectedAreSettled() {
            assertThat(declared().updateStatus(CustomsStatus.CLEARED, "t", "完了", DECLARED_AT)
                    .isSettled()).isTrue();
            assertThat(declared().updateStatus(CustomsStatus.REJECTED, "t", "不備", DECLARED_AT)
                    .isSettled()).isTrue();
        }
    }

    @Nested
    @DisplayName("留置が長引いているか（US29-6）")
    class WhenCheckingOverdue {

        private static final int THRESHOLD = 3;

        private static CustomsDeclaration heldAt(Instant heldAt) {
            return CustomsDeclaration.declare(NUMBER, BOOKING, TRACKING, DECLARED_AT)
                    .updateStatus(CustomsStatus.HELD, "tracker01", "書類不備", heldAt);
        }

        /**
         * <strong>3 日ちょうどでは督促しない。</strong>「3 日を超えたら」であり、
         * 境界を入れると対象が 1 日早まる。
         */
        @Test
        @DisplayName("留置 3 日ちょうどは、まだ督促の対象ではない")
        void doesNotWarnAtExactlyThreeDays() {
            Instant heldAt = Instant.parse("2027-09-02T00:00:00Z");
            LocalDate today = LocalDate.of(2027, Month.SEPTEMBER, 5);

            assertThat(heldAt(heldAt).isHeldOverdue(today, ZONE, THRESHOLD)).isFalse();
        }

        @Test
        @DisplayName("留置 4 日目から督促の対象になる")
        void warnsAfterThreeDays() {
            Instant heldAt = Instant.parse("2027-09-02T00:00:00Z");
            LocalDate today = LocalDate.of(2027, Month.SEPTEMBER, 6);

            assertThat(heldAt(heldAt).isHeldOverdue(today, ZONE, THRESHOLD)).isTrue();
        }

        /**
         * <strong>数えるのは、最新の留置遷移からである。</strong>
         *
         * <p>申告日から数えると、いったん通関して留め直された申告が、留め直した
         * 初日から「3 日超」と判定される。
         */
        @Test
        @DisplayName("いったん通関して留め直した申告は、留め直した日から数える")
        void countsFromTheLatestHeldTransition() {
            CustomsDeclaration reheld = declared()
                    .updateStatus(CustomsStatus.CLEARED, "t", "完了",
                            Instant.parse("2027-09-03T00:00:00Z"))
                    .updateStatus(CustomsStatus.HELD, "t", "再検査",
                            Instant.parse("2027-09-20T00:00:00Z"));

            assertThat(reheld.isHeldOverdue(LocalDate.of(2027, Month.SEPTEMBER, 22), ZONE, THRESHOLD)).isFalse();

            // **超える側も見る**（IT9 レビュー tester の指摘）。
            // 「まだ超えていない」だけでは、申告日時と留置日時を取り違えた実装
            // （どちらでも false になる日を選んでいる）を判別できない
            assertThat(reheld.isHeldOverdue(LocalDate.of(2027, Month.SEPTEMBER, 24), ZONE,
                            THRESHOLD))
                    .as("留め直した日から数えていない。申告日時から数えると、"
                            + "この日はとうに 3 日を超えている")
                    .isTrue();
        }

        /** 留置でなければ督促の対象ではない。 */
        @Test
        @DisplayName("留置でない申告は督促の対象にならない")
        void ignoresDeclarationsThatAreNotHeld() {
            assertThat(declared().isHeldOverdue(LocalDate.of(2099, Month.JANUARY, 1), ZONE, THRESHOLD))
                    .isFalse();
        }
    }

    /**
     * <strong>復元では検査しない</strong>（既存の行を壊さない）。
     *
     * <p>不変条件を足したとき、列が無かったころの行や規則が変わる前に入った行が
     * 読めなくなる。検査するのは<strong>新しく受け付けるとき</strong>だけである。
     *
     * <p><strong>この検査が無いと、コメントが宣言しているだけになる。</strong>
     * 誰かが復元にも検査を入れた瞬間、古い行を持つ環境だけが落ちる——手元では出ない。
     */
    @Nested
    @DisplayName("永続化された行から復元するとき")
    class WhenRestoring {

        @Test
        @DisplayName("履歴が無くても、通関済でも読み戻せる")
        void doesNotValidateOnRestore() {
            // 履歴が空の通関済——申告そのものが履歴に残る新規の形とは食い違う
            CustomsDeclaration restored = CustomsDeclaration.restore(1L, NUMBER, BOOKING,
                    TRACKING, DECLARED_AT, CustomsStatus.CLEARED, null, null, null);

            assertThat(restored.status()).isEqualTo(CustomsStatus.CLEARED);
            assertThat(restored.history()).isEmpty();
            assertThat(restored.isCleared()).isTrue();
        }
    }
}
