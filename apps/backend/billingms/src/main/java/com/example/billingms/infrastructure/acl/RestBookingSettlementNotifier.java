package com.example.billingms.infrastructure.acl;

import com.example.billingms.application.port.BookingSettlementNotifier;
import com.example.shared.auth.AuthenticatedUser;
import org.springframework.web.client.RestClient;

/**
 * 予約に精算の完了を知らせる（US23-4・[ADR-028] 決定 1）。
 *
 * <p><strong>相手の型は持ち込まない。</strong>運ぶのは予約番号だけである。
 *
 * <p><strong>失敗を握りつぶさない。</strong>相手が断ったこと（引取前・知らない予約）も、
 * 届かなかったことも、そのまま外へ出す——黙って捨てると、引取済のまま残った予約に
 * 誰も気づけない。入金の確認ごと巻き戻す方が、経理担当者にとって分かりやすい
 * （もう一度押せる）。
 */
// **経路は設定にしない。**相手との契約であり、環境ごとに変わるものではない
// （変わるのは所在＝ベース URL だけで、そちらは設定から受け取る）。契約テストが
// 両側の経路を突き合わせており、片方だけ直せば赤になる
@SuppressWarnings("java:S1075")
public class RestBookingSettlementNotifier implements BookingSettlementNotifier {

    /** 呼び出す経路。**契約と突き合わせる**（契約テスト）。 */
    public static final String SETTLEMENT_PATH = "/api/v1/bookings/{bookingId}/settlement";

    /** このサービス自身を表す主体。名乗らないと相手の [ADR-007] フィルタが一律に断る。 */
    public static final String SYSTEM_PRINCIPAL = "system:billingms";

    private final RestClient restClient;

    public RestBookingSettlementNotifier(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void markSettled(String bookingId) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder.path(SETTLEMENT_PATH).build(bookingId))
                .header(AuthenticatedUser.USER_ID_HEADER, SYSTEM_PRINCIPAL)
                .retrieve()
                .toBodilessEntity();
    }
}
