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
            assertThat(shipper.email()).isEqualTo("yamada@example.com");
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
            assertThat(restored.email()).isEqualTo("not-an-email");
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
                    "契約番号なし商事", "old@example.com", "東京都", null, null);

            assertThat(restored.contractNumber()).isEmpty();
            assertThat(restored.isCorporate()).isTrue();
        }
    }
}
