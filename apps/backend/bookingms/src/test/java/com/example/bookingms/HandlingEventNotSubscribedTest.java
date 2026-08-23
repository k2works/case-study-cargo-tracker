package com.example.bookingms;

import com.example.shared.architecture.EventSubscriptionRules;
import com.example.shared.architecture.HexagonalArchitectureRules;
import com.example.shared.contract.HandlingActivityRegisteredContract;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [ADR-023] 決定 6 のコンプライアンス。<strong>IT7 で購読するのは trackingms だけ</strong>。
 *
 * <p>bookingms 側の処理は「誤配で {@code RoutingStatus} を動かす」ことであり、それは
 * US28（IT10）である。いまは事実として購読していないが、<strong>決定を検査に落とさないと</strong>、
 * IT10 で購読を足すときに「決定を意図的に覆したのか、写し漏れたのか」が区別できない。
 *
 * <p>この検査が落ちたら、それは<strong>US28 に着手した合図</strong>である。
 * ADR-023 決定 6 を更新してからこの検査を外すこと。
 */
@DisplayName("荷役のイベントを購読しない（ADR-023 決定 6）")
class HandlingEventNotSubscribedTest {

    private final JavaClasses classes =
            HexagonalArchitectureRules.importProductionClasses("com.example.bookingms");

    @Test
    @DisplayName("bookingms は荷役のイベントを購読していない")
    void doesNotSubscribeToHandlingEvents() {
        EventSubscriptionRules.doesNotSubscribeTo(
                        HandlingActivityRegisteredContract.ROUTING_KEY,
                        "誤配で RoutingStatus を動かすのは US28・IT10")
                .check(classes);
    }
}
