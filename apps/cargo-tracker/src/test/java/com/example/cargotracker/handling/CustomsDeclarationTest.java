package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.handling.domain.model.CustomsDeclaration;
import com.example.cargotracker.handling.domain.model.CustomsStatus;
import com.example.cargotracker.handling.domain.model.DeclarationNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 通関申告の不変条件（US29）。
 *
 * <p><strong>通関は業務上の唯一の「止まる仕組み」である。</strong> 誤配も荷受人違いも
 * 「起きた事実」として記録するが、<strong>通関前の引き渡しは実行してはならない</strong>。
 * 記録の対象ではなく、拒否の対象である。
 *
 * <p>日時は {@code Clock} 相対で作る。<strong>絶対日付を書かない。</strong>
 */
@DisplayName("通関申告の不変条件（US29）")
class CustomsDeclarationTest {

    /** 業務のタイムゾーン。**留置の日数は日付単位で数える。** */
    private static final ZoneId 業務 = ZoneId.of("Asia/Tokyo");

    private static final Instant 申告 = Instant.parse("2026-09-05T02:00:00Z");
    private static final Instant 現在 = Instant.parse("2026-09-06T02:00:00Z");

    private static CustomsDeclaration 審査中の申告() {
        return CustomsDeclaration.declare(
                new DeclarationNumber("DEC-2026-0001"), 申告);
    }

    @Nested
    @DisplayName("登録する")
    class 登録する {

        /** 受入基準: 申告番号・申告日時を入力して登録できる（<strong>初期状態は審査中</strong>）。 */
        @Test
        void 登録した申告は審査中で始まる() {
            CustomsDeclaration declaration = 審査中の申告();

            assertThat(declaration.status()).isEqualTo(CustomsStatus.PENDING);
            assertThat(declaration.declaredAt()).isEqualTo(申告);
            assertThat(declaration.isCleared()).isFalse();
        }

        @Test
        void 申告番号は必須である() {
            assertThatThrownBy(() -> assertThat(
                    CustomsDeclaration.declare(null, 申告)).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申告番号は必須です");
        }

        @Test
        void 申告日時は必須である() {
            assertThatThrownBy(() -> assertThat(CustomsDeclaration.declare(
                    new DeclarationNumber("DEC-2026-0002"), null)).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("申告日時は必須です");
        }
    }

    @Nested
    @DisplayName("状態を更新する")
    class 状態を更新する {

        /** 受入基準: 通関済・留置・不可に更新できる。<strong>理由は必須。</strong> */
        @Test
        void 理由を添えて通関済にできる() {
            CustomsDeclaration declaration = 審査中の申告();

            var change = declaration.updateStatus(
                    CustomsStatus.CLEARED, "書類に不備なし", "handler", 現在);

            assertThat(declaration.status()).isEqualTo(CustomsStatus.CLEARED);
            assertThat(declaration.isCleared()).isTrue();
            assertThat(declaration.clearedAt()).isEqualTo(現在);
            assertThat(change.from()).isEqualTo(CustomsStatus.PENDING);
            assertThat(change.to()).isEqualTo(CustomsStatus.CLEARED);
            assertThat(change.reason()).contains("不備なし");
            assertThat(change.changedBy()).isEqualTo("handler");
        }

        /**
         * <strong>理由の無い更新は受け付けない。</strong>
         *
         * <p>通関状態は貨物を止めたり通したりする。<strong>なぜそう判断したのかが
         * 残らないと、後から誰も検証できない。</strong>
         */
        @Test
        void 理由の無い更新は受け付けない() {
            CustomsDeclaration declaration = 審査中の申告();

            assertThatThrownBy(() -> assertThat(declaration.updateStatus(
                    CustomsStatus.HELD, "  ", "handler", 現在)).isNotNull())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("理由は必須です");
        }

        /**
         * <strong>同じ状態への更新は受け付けない。</strong>
         *
         * <p>変わっていないものを履歴に積むと、「何回留置されたのか」が読めなくなる。
         */
        @Test
        void 同じ状態への更新は受け付けない() {
            CustomsDeclaration declaration = 審査中の申告();

            assertThatThrownBy(() -> assertThat(declaration.updateStatus(
                    CustomsStatus.PENDING, "変更なし", "handler", 現在)).isNotNull())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("すでに");
        }

        /**
         * <strong>通関済からは戻さない。</strong>
         *
         * <p>通関が下りたことを根拠に引き渡しが進む。後から取り消せる形にすると、
         * <strong>引き渡し済みの貨物が「通関していない」状態になりうる</strong>。
         * 誤って通関済にした場合は取り消し（US36 の系列）で扱う。
         */
        @Test
        void 通関済からは戻せない() {
            CustomsDeclaration declaration = 審査中の申告();
            declaration.updateStatus(CustomsStatus.CLEARED, "問題なし", "handler", 現在);

            assertThatThrownBy(() -> assertThat(declaration.updateStatus(
                    CustomsStatus.HELD, "やり直し", "handler", 現在)).isNotNull())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("通関済");
        }

        /**
         * <strong>不可にできる。</strong> 受入基準は「通関済」「留置」「不可」の 3 つを挙げている。
         *
         * <p><strong>不可は留置より重い。</strong> 積戻し・廃棄・関税の争いに発展する。
         * 留置と同じく対応が要る状態として扱う。
         */
        @Test
        void 不可にできて引取は許さない() {
            CustomsDeclaration declaration = 審査中の申告();

            var change = declaration.updateStatus(
                    CustomsStatus.REJECTED, "禁制品に該当", "handler", 現在);

            assertThat(declaration.status()).isEqualTo(CustomsStatus.REJECTED);
            assertThat(declaration.allowsClaim()).isFalse();
            assertThat(change.to().needsAttention())
                    .as("不可も追跡担当者の対応が要る（留置だけを拾うと最も重い状態が最も静かになる）")
                    .isTrue();
        }

        /** <strong>通関済は対応が要る状態ではない。</strong> 常に true を返す実装で緑にしない。 */
        @Test
        void 通関済と審査中は対応の対象にしない() {
            assertThat(CustomsStatus.CLEARED.needsAttention()).isFalse();
            assertThat(CustomsStatus.PENDING.needsAttention()).isFalse();
            assertThat(CustomsStatus.HELD.needsAttention()).isTrue();
            assertThat(CustomsStatus.REJECTED.needsAttention()).isTrue();
        }

        /** 留置からは解除できる（審査は続いている）。 */
        @Test
        void 留置からは通関済にできる() {
            CustomsDeclaration declaration = 審査中の申告();
            declaration.updateStatus(CustomsStatus.HELD, "書類不備", "handler", 現在);

            declaration.updateStatus(CustomsStatus.CLEARED, "不備を解消", "handler", 現在);

            assertThat(declaration.isCleared()).isTrue();
        }
    }

    @Nested
    @DisplayName("留置が長引く")
    class 留置が長引く {

        /**
         * 受入基準: <strong>「留置」のまま 3 日を超えた申告</strong>は警告の対象になる。
         *
         * <p><strong>日付単位で数える。</strong> 経過時間（72 時間）で測ると、
         * 時差の分だけ境界がずれる（業務日付は UTC で判断しない）。
         */
        @Test
        void 留置から3日を超えると警告の対象になる() {
            CustomsDeclaration declaration = 審査中の申告();
            declaration.updateStatus(CustomsStatus.HELD, "書類不備", "handler", 現在);

            LocalDate heldOn = LocalDate.ofInstant(現在, 業務);

            assertThat(declaration.heldTooLong(heldOn.plusDays(3), 業務)).isFalse();
            assertThat(declaration.heldTooLong(heldOn.plusDays(4), 業務)).isTrue();
        }

        /** <strong>留置でなければ警告の対象にしない。</strong> 通関済は何日経っても仕事ではない。 */
        @Test
        void 留置でなければ日数に関わらず警告の対象にしない() {
            CustomsDeclaration declaration = 審査中の申告();
            declaration.updateStatus(CustomsStatus.CLEARED, "問題なし", "handler", 現在);

            assertThat(declaration.heldTooLong(
                    LocalDate.ofInstant(現在, 業務).plusDays(30), 業務)).isFalse();
        }

        /**
         * <strong>審査に戻してから再び留置したら、数え直す。</strong>
         *
         * <p>最初の留置日から数え続けると、審査に戻して 1 日で再留置した申告が
         * いきなり警告になる。<strong>いま何日止まっているか</strong>が知りたい。
         *
         * <p>留置を解く先は<strong>通関済とは限らない</strong>。書類を出し直して
         * 審査に戻ることがあり、そのときはまだ通関していない。
         */
        @Test
        void 審査に戻して再び留置したら日数を数え直す() {
            CustomsDeclaration declaration = 審査中の申告();
            declaration.updateStatus(CustomsStatus.HELD, "書類不備", "handler", 現在);
            Instant tenDaysLater = 現在.plusSeconds(10 * 24 * 3600);
            declaration.updateStatus(CustomsStatus.PENDING, "書類を再提出", "handler", tenDaysLater);
            declaration.updateStatus(CustomsStatus.HELD, "再検査", "handler", tenDaysLater);

            assertThat(declaration.heldTooLong(
                    LocalDate.ofInstant(tenDaysLater, 業務).plusDays(1), 業務)).isFalse();
            // **通関はしていない。** 留置が解けたことと通ったことは別である
            assertThat(declaration.allowsClaim()).isFalse();
        }
    }

    /**
     * <strong>通関済でなければ引取は拒否する</strong>（ビジネスルール 2）。
     *
     * <p>これは警告ではない。誤配・荷受人違いは「起きた事実」として記録するが、
     * <strong>通関前の引き渡しは業務として実行してはならない</strong>。
     */
    @Test
    void 通関済でなければ引取を許さない() {
        CustomsDeclaration declaration = 審査中の申告();

        assertThat(declaration.allowsClaim()).isFalse();

        declaration.updateStatus(CustomsStatus.CLEARED, "問題なし", "handler", 現在);

        assertThat(declaration.allowsClaim()).isTrue();
    }
}
