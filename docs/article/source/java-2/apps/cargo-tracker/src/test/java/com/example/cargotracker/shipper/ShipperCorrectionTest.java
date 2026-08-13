package com.example.cargotracker.shipper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperCode;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperContact;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperType;
import org.junit.jupiter.api.Test;

/** US32: 荷主情報を訂正する。ドメインの振る舞いをユニットテストで固定する。 */
class ShipperCorrectionTest {

    private static Shipper 荷主() {
        return new Shipper(
                ShipperId.generate(),
                ShipperCode.of(1),
                ShipperType.INDIVIDUAL,
                new ShipperName("山田太朗"),
                new ShipperContact(
                        new Email("info@yamada.example.com"),
                        new Phone("06-1234-5678"),
                        new Address("JP", "530-0001", "大阪府", "大阪市北区", "梅田 1-1-1")),
                // 個人荷主は契約を持たない（US03 で種別と契約をひと組にした）
                null,
                5L);
    }

    @Test
    void 荷主名を訂正できる() {
        Shipper corrected = 荷主().rename(new ShipperName("山田太郎"));

        assertThat(corrected.name().value()).isEqualTo("山田太郎");
    }

    @Test
    void 連絡先を訂正できる() {
        Shipper corrected = 荷主()
                .changeContact(new Email("sales@yamada.example.com"), new Phone("06-9999-0000"));

        assertThat(corrected.email().value()).isEqualTo("sales@yamada.example.com");
        assertThat(corrected.phone().value()).isEqualTo("06-9999-0000");
    }

    @Test
    void 住所を訂正できる() {
        Shipper corrected = 荷主()
                .relocate(new Address("JP", "552-0021", "大阪府", "大阪市港区", "築港 1-1-1"));

        assertThat(corrected.address().city()).isEqualTo("大阪市港区");
    }

    /**
     * 荷主コードと荷主種別は訂正で変わらない（US32 の受入基準）。
     *
     * <p>識別子と区分の変更は訂正ではなく別の荷主である。**振る舞いの側に
     * 変更する手段が無いことを固定しておかないと、画面から欄を消しただけの
     * 「見えないだけの変更可能性」が残る。**
     */
    @Test
    void 訂正しても荷主コードと種別は変わらない() {
        Shipper original = 荷主();
        Shipper corrected = original
                .rename(new ShipperName("別名"))
                .changeContact(new Email("other@example.com"), new Phone("06-0000-0000"))
                .relocate(new Address("JP", "100-0001", "東京都", "千代田区", "1-1"));

        assertThat(corrected.shipperCode()).isEqualTo(original.shipperCode());
        assertThat(corrected.shipperType()).isEqualTo(original.shipperType());
        assertThat(corrected.id()).isEqualTo(original.id());
    }

    /** 訂正はバージョンを進めない。進めるのは更新が成功した DB 側である。 */
    @Test
    void 訂正してもバージョンは読み取り時のまま() {
        assertThat(荷主().rename(new ShipperName("別名")).version()).isEqualTo(5L);
    }

    /** 元のインスタンスは変わらない（値としての振る舞い）。 */
    @Test
    void 訂正しても元のインスタンスは変わらない() {
        Shipper original = 荷主();
        Shipper ignored = original.rename(new ShipperName("別名"));

        assertThat(ignored.name().value()).isEqualTo("別名");
        assertThat(original.name().value()).isEqualTo("山田太朗");
    }
}
