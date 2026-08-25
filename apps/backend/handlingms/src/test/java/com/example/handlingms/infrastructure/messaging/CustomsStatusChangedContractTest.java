package com.example.handlingms.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.handlingms.application.port.CustomsStatusChanged;
import com.example.handlingms.domain.model.CustomsStatus;
import com.example.shared.contract.CustomsStatusChangedContract;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「通関状態が変わった」の契約（US29-5）。
 *
 * <p><strong>送り手と受け手が同じものを見ていることを確かめる。</strong>項目名・順序・
 * 語彙のどれか 1 つがずれると、<strong>送り手はエラーにならないまま届かない</strong>
 * ——受け手は「知らない状態」として何もせず、デッドレターにも行かない。
 */
@DisplayName("通関状態のイベントの契約")
class CustomsStatusChangedContractTest {

    @Test
    @DisplayName("流れる項目が、契約と一致する")
    void matchesTheAgreedFields() {
        List<String> fields = Arrays.stream(CustomsStatusChanged.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fields)
                .as("契約と項目が食い違っている。受け手が読めない形になる")
                .isEqualTo(CustomsStatusChangedContract.FIELDS);
    }

    /**
     * <strong>状態の語彙が、送り手の列挙と一致する。</strong>
     *
     * <p>送り手が状態を足したり改名したりすると、受け手は「知らない状態」として
     * 何もしない。項目名だけを固定しても、この 1 項目が素通りになる。
     */
    @Test
    @DisplayName("状態の語彙が、送り手の列挙と一致する")
    void matchesTheAgreedStatuses() {
        List<String> statuses = Arrays.stream(CustomsStatus.values()).map(Enum::name).toList();

        assertThat(statuses)
                .as("契約と語彙が食い違っている。受け手は知らない状態として何もしない")
                .containsExactlyInAnyOrderElementsOf(CustomsStatusChangedContract.STATUSES);
    }

    /** 交換機とルーティングキーは、契約から取る。**綴りを写し間違えると黙って届かない**。 */
    @Test
    @DisplayName("交換機とルーティングキーが、契約と一致する")
    void matchesTheAgreedChannel() {
        assertThat(HandlingEventChannels.EXCHANGE)
                .isEqualTo(CustomsStatusChangedContract.EXCHANGE);
        assertThat(HandlingEventChannels.CUSTOMS_STATUS_CHANGED)
                .isEqualTo(CustomsStatusChangedContract.ROUTING_KEY);
    }
}
