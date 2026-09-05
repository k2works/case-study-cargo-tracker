package com.example.cargotracker.booking.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.booking.infrastructure.persistence.ShipperMapper;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.domain.attention.AttentionItemId;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 投影の振る舞いを実 DB で固定する。
 *
 * <p>ここで守るのはメールアドレス一意の三段のうち 2 段目（UNIQUE で弾く）と
 * 3 段目（弾いた行を要確認一覧に残す）。1 段目の存在確認は同時登録のレースで
 * 素通りするので、この 2 本が本当に踏まれることを固定する（domain-model.md）。</p>
 */
@SpringBootTest
// クラスが終わったらコンテキストを閉じる。閉じないと複数のコンテキストが同時に
// 生きたまま同じ Axon Server にハンドラを登録し、二重登録で起動に失敗する
// （DuplicateQueryHandlerSubscriptionException）。落ちるテストが実行順で変わる。
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperProjectionIT extends AbstractAxonIntegrationTest {

    @Autowired
    private ShipperProjection projection;

    @Autowired
    private ShipperMapper shippers;

    @Autowired
    private AttentionItemMapper attentionItems;

    /**
     * 投影に届く時点では復号済み（ADR-0003 決定 1。復号は Converter の責務）。
     * 鍵が破棄されていれば、届く値が null になる。
     */
    private ShipperRegisteredEvent event(String shipperId, String name, String email) {
        return new ShipperRegisteredEvent(shipperId, "CORPORATE", name, email,
                "03-0000-0000", "東京都港区", "CT-0001", "0.1000");
    }

    /** 鍵を破棄したあとに Converter が渡してくる形（個人情報が null）。 */
    private ShipperRegisteredEvent shreddedEvent(String shipperId) {
        return new ShipperRegisteredEvent(shipperId, "CORPORATE", null, null, null, null,
                "CT-0001", "0.1000");
    }

    @Test
    @DisplayName("荷主が投影され、荷主コードは投影側で採番される")
    void projectsShipper() {
        String id = "SHP-IT-" + System.nanoTime();
        projection.on(event(id, "山田商事", id + "@example.com"));

        ShipperMapper.ShipperRow row = shippers.findById(id);
        assertThat(row).isNotNull();
        assertThat(row.name()).isEqualTo("山田商事");
        assertThat(row.shipperCode())
                .as("採番は投影側のシーケンス。集約で MAX+1 しない")
                .matches("SHP-\\d{6}");
    }

    @Test
    @DisplayName("同じメールアドレスの 2 件目は UNIQUE で弾かれ、要確認一覧に残る")
    void rejectsDuplicateEmailAndRecordsAttention() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        String first = "SHP-IT-A" + System.nanoTime();
        String second = "SHP-IT-B" + System.nanoTime();

        projection.on(event(first, "山田商事", email));
        projection.on(event(second, "山田商事（新）", email));

        assertThat(shippers.findById(second))
                .as("2 段目: 投影テーブルの UNIQUE が最後の砦")
                .isNull();

        assertThat(attentionItems.findOpenByRole("ROLE_SALES"))
                .as("3 段目: 弾いた行が見えないと「登録したのに一覧に出ない」が誰にも分からない")
                .anySatisfy(item -> {
                    assertThat(item.targetId()).isEqualTo(second);
                    assertThat(item.reason()).isEqualTo("メールアドレスの重複");
                    assertThat(item.assignedRole()).isEqualTo("ROLE_SALES");
                    assertThat(item.itemId())
                            .as("識別子は採番でなく事実からの導出。本番経路が共有カーネルの"
                                    + "導出を通っていなければ、投影を読み直すたびに行が積み上がる")
                            .isEqualTo(AttentionItemId.of(item.kind(), item.targetType(),
                                    item.targetId(), item.reason()).value());
                });
    }

    @Test
    @DisplayName("同じイベントを 2 度読んでも行は増えない（リプレイの冪等性）")
    void isIdempotentOnReplay() {
        String id = "SHP-IT-R" + System.nanoTime();
        ShipperRegisteredEvent stored = event(id, "山田商事", id + "@example.com");

        projection.on(stored);
        String codeAfterFirst = shippers.findById(id).shipperCode();
        projection.on(stored);

        assertThat(shippers.findById(id).shipperCode())
                .as("リプレイのたびに採番し直すと荷主コードが変わってしまう")
                .isEqualTo(codeAfterFirst);
        assertThat(attentionItems.findOpenByRole("ROLE_SALES"))
                .as("リプレイを重複として要確認一覧に積み上げない")
                .noneSatisfy(item -> assertThat(item.targetId()).isEqualTo(id));
    }

    @Test
    @DisplayName("個人の荷主は契約番号も割引率も持たない")
    void projectsIndividualWithoutContract() {
        String id = "SHP-IT-I" + System.nanoTime();
        projection.on(new ShipperRegisteredEvent(id, "INDIVIDUAL",
                "山田太郎", id + "@example.com", "03-0000-0000", "東京都港区",
                null, null));

        ShipperMapper.ShipperRow row = shippers.findById(id);
        assertThat(row.contractNumber()).isNull();
        assertThat(row.discountRate()).isNull();
        assertThat(row.shipperType()).isEqualTo("INDIVIDUAL");
    }

    @Test
    @DisplayName("鍵を破棄したあとにリプレイすると個人情報が消える（投影は止まらない）")
    void personalDataDisappearsAfterKeyIsDestroyed() {
        String id = "SHP-IT-S" + System.nanoTime();

        projection.on(shreddedEvent(id));

        ShipperMapper.ShipperRow row = shippers.findById(id);
        assertThat(row).as("鍵が無くても投影は止まらない").isNotNull();
        assertThat(row.name()).isNull();
        assertThat(row.email()).isNull();
        assertThat(row.phone()).isNull();
        assertThat(row.address()).isNull();
        assertThat(row.shipperType()).as("個人情報でない列は残る").isEqualTo("CORPORATE");
    }

    @Test
    @DisplayName("弾いた記録に個人情報を書かない")
    void doesNotWritePersonalDataIntoAttentionItem() {
        // attention_item は追記専用でリプレイでも消さない。ここに平文で書くと
        // **鍵を破棄しても消えない**。弾かれた荷主は shipper に行が無いので、
        // 削除要求を処理する担当がその shipperId に辿り着けない（ADR-0003 決定 6）。
        String email = "attention-pii-" + System.nanoTime() + "@example.com";
        String firstId = "SHP-PII-1-" + System.nanoTime();
        projection.on(event(firstId, "山田商事", email));

        String rejectedId = "SHP-PII-2-" + System.nanoTime();
        projection.on(event(rejectedId, "重複商事", email));

        AttentionItemMapper.AttentionItemRow row = attentionItems.findOpenByRole("ROLE_SALES")
                .stream()
                .filter(item -> rejectedId.equals(item.targetId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("弾いた記録が残っていない"));

        assertThat(row.payload())
                .as("メールアドレスも氏名も書かない。識別子だけを持つ")
                .doesNotContain(email)
                .doesNotContain("重複商事")
                .contains(firstId);
    }
}
