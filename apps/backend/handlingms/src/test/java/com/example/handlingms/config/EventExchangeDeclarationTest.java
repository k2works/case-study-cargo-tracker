package com.example.handlingms.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shared.contract.EventExchangeContract;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;

/**
 * 業務のイベントを流す交換機の宣言（[ADR-022] 決定 4・IT8 返済枠 0.1）。
 *
 * <p><strong>名前が一致しているだけでは足りない。</strong>交換機は耐久性・自動削除・引数まで
 * 含めて同じでなければ再宣言できず、食い違うと後から接続したほうが
 * {@code PRECONDITION_FAILED - inequivalent arg} で落ちる。しかも既存の交換機は宣言し直せない
 * ため、落ちたサービスは後続のキュー宣言まで止まる。
 *
 * <p>IT7 の kind 統合で実際に踏んだ。<strong>Testcontainers は毎回まっさらな交換機を作るので、
 * この壊れ方は結合テストでは出ない</strong>。守っているのがコメントだけだったため、契約と
 * 突き合わせる。
 */
@DisplayName("交換機の宣言が契約と一致する（handlingms）")
class EventExchangeDeclarationTest {

    private final HandlingConfig config = new HandlingConfig();

    @Test
    @DisplayName("業務の交換機は、耐久性・自動削除・引数まで契約どおりに宣言される")
    void declaresBusinessExchangesAsAgreed() {
        for (TopicExchange exchange : businessExchanges()) {
            assertThat(exchange.isDurable())
                    .as("%s の耐久性が契約と違う", exchange.getName())
                    .isEqualTo(EventExchangeContract.DURABLE);
            assertThat(exchange.isAutoDelete())
                    .as("%s の自動削除が契約と違う", exchange.getName())
                    .isEqualTo(EventExchangeContract.AUTO_DELETE);
            assertThat(exchange.getArguments())
                    .as("%s の引数が契約と違う。既存環境では宣言し直せない", exchange.getName())
                    .containsExactlyInAnyOrderEntriesOf(EventExchangeContract.ARGUMENTS);
        }
    }

    /** 行き場のないイベントの受け皿の名前も、全サービスで同じ 1 つを読む。 */
    @Test
    @DisplayName("予備の行き先の名前が契約と一致する")
    void namesTheUnroutableExchangeAsAgreed() {
        assertThat(com.example.handlingms.infrastructure.messaging.HandlingEventChannels.UNROUTABLE_EXCHANGE).isEqualTo(EventExchangeContract.UNROUTABLE_EXCHANGE);
        assertThat(com.example.handlingms.infrastructure.messaging.HandlingEventChannels.UNROUTABLE_QUEUE).isEqualTo(EventExchangeContract.UNROUTABLE_QUEUE);
    }

    private List<TopicExchange> businessExchanges() {
        return List.of(config.cargoHandlingExchange());
    }
}
