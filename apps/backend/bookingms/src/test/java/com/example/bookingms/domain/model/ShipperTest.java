package com.example.bookingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("荷主")
class ShipperTest {

    @Nested
    @DisplayName("新規登録")
    class Registration {

        @Test
        @DisplayName("個人荷主を登録できる")
        void registersIndividual() {
            Shipper shipper = Shipper.register(
                    ShipperType.INDIVIDUAL, "山田太郎", "yamada@example.com",
                    "東京都千代田区 1-1-1", "03-1234-5678");

            assertThat(shipper.type()).isEqualTo(ShipperType.INDIVIDUAL);
            assertThat(shipper.name()).isEqualTo("山田太郎");
            assertThat(shipper.email().value()).isEqualTo("yamada@example.com");
        }

        @Test
        @DisplayName("法人荷主を登録できる（契約番号は必須・ADR-012）")
        void registersCorporate() {
            Shipper shipper = Shipper.register(
                    ShipperType.CORPORATE, "伊藤商事株式会社", "info@ito.example.com",
                    "大阪府大阪市 2-2-2", "06-1234-5678",
                    new CorporateContract(ContractNumber.of("CN-2026-0100"), null));

            assertThat(shipper.type()).isEqualTo(ShipperType.CORPORATE);
        }

        @Test
        @DisplayName("登録直後は荷主コードを持たない（採番は永続化の経路で行う）")
        void hasNoCodeBeforePersisted() {
            // 自前で採番するとシーケンスと衝突し、原因でない他の処理が UNIQUE 制約で落ちる
            Shipper shipper = Shipper.register(
                    ShipperType.INDIVIDUAL, "山田太郎", "y@example.com", "住所", null);

            assertThat(shipper.shipperCode()).isNull();
        }

        @Test
        @DisplayName("連絡先は任意（メールアドレスがあれば連絡は取れる）")
        void allowsMissingPhone() {
            Shipper shipper = Shipper.register(
                    ShipperType.INDIVIDUAL, "山田太郎", "y@example.com", "住所", null);

            assertThat(shipper.phone()).isNull();
        }
    }

    @Nested
    @DisplayName("入力の検査")
    class Validation {

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("氏名/社名は必須")
        void rejectsBlankName(String name) {
            assertThatThrownBy(() -> Shipper.register(
                    ShipperType.INDIVIDUAL, name, "y@example.com", "住所", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("氏名");
        }

        @Test
        @DisplayName("住所は必須")
        void rejectsBlankAddress() {
            assertThatThrownBy(() -> Shipper.register(
                    ShipperType.INDIVIDUAL, "山田太郎", "y@example.com", " ", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("住所");
        }

        @ParameterizedTest
        @ValueSource(strings = {"yamada", "yamada@", "@example.com", "yamada example.com"})
        @DisplayName("メールアドレスの形をなしていなければ拒否する")
        void rejectsMalformedEmail(String email) {
            // 形が崩れたまま登録すると、重複判定も連絡もできない荷主が残る
            assertThatThrownBy(() -> Shipper.register(
                    ShipperType.INDIVIDUAL, "山田太郎", email, "住所", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("メールアドレス");
        }

        @Test
        @DisplayName("種別は必須")
        void rejectsMissingType() {
            assertThatThrownBy(() -> Shipper.register(
                    null, "山田太郎", "y@example.com", "住所", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("種別");
        }
    }

    @Nested
    @DisplayName("復元")
    class Restoration {

        @Test
        @DisplayName("永続化された行は検査せずそのまま読む")
        void restoresWithoutValidation() {
            // 検査を後から足すと、その規則が無かったころの行が読めなくなる。
            // 新規受け入れ時だけ検査し、復元では検査しない
            Shipper restored = Shipper.restore(
                    1L, "SHP-000001", ShipperType.INDIVIDUAL, "旧データ", "not-an-email", "住所", null);

            assertThat(restored.shipperCode()).isEqualTo("SHP-000001");
            assertThat(restored.email().value()).isEqualTo("not-an-email");
        }
    }

    @Nested
    @DisplayName("法人の契約情報（US03）")
    class CorporateContractSpec {

        private Shipper corporate(ContractNumber contractNumber, DiscountRate discountRate) {
            return Shipper.register(ShipperType.CORPORATE, "丸紅商事株式会社", "corp@example.com",
                    "東京都千代田区 1-1-1", "03-1234-5678",
                    contractNumber == null ? null : new CorporateContract(contractNumber, discountRate));
        }

        @Test
        @DisplayName("契約番号と割引率を持てる")
        void holdsContract() {
            Shipper shipper = corporate(
                    ContractNumber.of("CN-2026-0001"), DiscountRate.ofPercent(new BigDecimal("12.5")));

            assertThat(shipper.contractNumber()).contains(ContractNumber.of("CN-2026-0001"));
            assertThat(shipper.discountRate())
                    .contains(DiscountRate.ofPercent(new BigDecimal("12.5")));
            assertThat(shipper.isCorporate()).isTrue();
        }

        @Test
        @DisplayName("割引率は未設定でよい（交渉が終わっていないことがある）")
        void allowsUnsetDiscountRate() {
            Shipper shipper = corporate(ContractNumber.of("CN-2026-0002"), null);

            // 0% で埋めると、設定漏れが「割引なしの契約」として通る
            assertThat(shipper.discountRate()).isEmpty();
        }

        @Test
        @DisplayName("契約番号の無い法人は受け付けない")
        void rejectsCorporateWithoutContractNumber() {
            // 許すと契約番号が空の法人が溜まり、US22 で全件の追加入力が発生する
            // 契約情報を組み立てないケース。ラムダ内で組み立てると、そちらが投げただけでも通る
            assertThatThrownBy(() -> corporate(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("契約番号");
        }

        @Test
        @DisplayName("個人に契約情報は持たせない")
        void rejectsContractOnIndividual() {
            // 付け忘れと同じく、付けすぎも誤り
            CorporateContract contract =
                    new CorporateContract(ContractNumber.of("CN-2026-0003"), null);

            assertThatThrownBy(() -> Shipper.register(ShipperType.INDIVIDUAL, "山田太郎",
                    "yamada@example.com", "東京都", "03-0000-0000", contract))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("法人");
        }

        @Test
        @DisplayName("個人は契約情報を持たない")
        void individualHasNoContract() {
            Shipper shipper = Shipper.register(ShipperType.INDIVIDUAL, "山田太郎",
                    "yamada@example.com", "東京都", "03-0000-0000");

            assertThat(shipper.contractNumber()).isEmpty();
            assertThat(shipper.discountRate()).isEmpty();
            assertThat(shipper.isCorporate()).isFalse();
        }

        @Test
        @DisplayName("復元では検査しない（列が無かったころの行が読めなくなる）")
        void restoreDoesNotValidate() {
            Shipper restored = Shipper.restore(1L, "SHP-000001", ShipperType.CORPORATE,
                    ShipperProfile.of("契約番号なし商事", "old@example.com", "東京都", null), null);

            assertThat(restored.contractNumber()).isEmpty();
            assertThat(restored.isCorporate()).isTrue();
        }
    }

    @Nested
    @DisplayName("編集（US02 / #550）")
    class Editing {

        private static Shipper registered() {
            return Shipper.restore(1L, "SHP-000001", ShipperType.CORPORATE,
                    ShipperProfile.of("丸紅商事", "marubeni@example.com", "東京都千代田区 1-1-1",
                            "03-1234-5678"),
                    new CorporateContract(ContractNumber.of("C-0001"),
                            DiscountRate.ofPercent(new BigDecimal("5"))));
        }

        /**
         * 編集しても荷主コードと id は変わらない。
         *
         * <p>コードが変わると、予約から見た荷主が別人になる。**採番し直すのは新規登録だけ**である。
         */
        @Test
        @DisplayName("連絡先を直しても、荷主コードと id は変わらない")
        void keepsIdentityWhenProfileChanges() {
            Shipper edited = registered().edit(
                    ShipperProfile.of("丸紅商事", "sales@marubeni.example.com", "東京都港区 2-2-2",
                            "03-9999-8888"),
                    new CorporateContract(ContractNumber.of("C-0001"),
                            DiscountRate.ofPercent(new BigDecimal("5"))));

            assertThat(edited.id()).isEqualTo(1L);
            assertThat(edited.shipperCode()).isEqualTo("SHP-000001");
            assertThat(edited.email().value()).isEqualTo("sales@marubeni.example.com");
            assertThat(edited.address()).isEqualTo("東京都港区 2-2-2");
        }

        /**
         * 編集でも新規登録と同じ検査を通す。
         *
         * <p>登録のときだけ検査すると、編集で不正な値を入れられる。**入口ごとに検査が違うと、
         * 緩いほうの入口から壊れた値が入る。**
         */
        @Test
        @DisplayName("編集でもメールアドレスの形式を検査する")
        void validatesEmailOnEdit() {
            // 形式の検査は EmailAddress が持つ（ADR-012）。壊れた値は連絡先を
            // 組み立てる時点で断られ、集約まで届かない
            assertThatThrownBy(() -> ShipperProfile.of(
                    "丸紅商事", "こわれたアドレス", "東京都港区 2-2-2", "03-9999-8888"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("メールアドレスの形式が不正です");
        }

        @Test
        @DisplayName("編集でメールアドレスが欠けていれば集約が拒む")
        void rejectsMissingEmailOnEdit() {
            assertThatThrownBy(() -> registered().edit(
                    new ShipperProfile("丸紅商事", null, "東京都港区 2-2-2", "03-9999-8888"),
                    new CorporateContract(ContractNumber.of("C-0001"), null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("メールアドレス");
        }

        @Test
        @DisplayName("編集でも氏名/社名と住所は必須")
        void validatesRequiredFieldsOnEdit() {
            CorporateContract contract =
                    new CorporateContract(ContractNumber.of("C-0001"), null);

            assertThatThrownBy(() -> registered().edit(
                    ShipperProfile.of("", "marubeni@example.com", "東京都港区 2-2-2", "03-9999-8888"),
                    contract))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registered().edit(
                    ShipperProfile.of("丸紅商事", "marubeni@example.com", "", "03-9999-8888"),
                    contract))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 種別は編集で変えられない。個人と法人ではその後に成り立つ規則が違う。 */
        @Test
        @DisplayName("法人の契約情報は編集できるが、個人に契約情報は付けられない")
        void keepsContractRuleOnEdit() {
            Shipper individual = Shipper.restore(2L, "SHP-000002", ShipperType.INDIVIDUAL,
                    ShipperProfile.of("山田太郎", "yamada@example.com", "東京都新宿区 3-3-3", null),
                    null);

            assertThatThrownBy(() -> individual.edit(individual.profile(),
                    new CorporateContract(ContractNumber.of("C-0002"), null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("法人から契約番号を外すことはできない")
        void requiresContractForCorporateOnEdit() {
            assertThatThrownBy(() -> registered().edit(registered().profile(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("契約番号");
        }
    }
}