package com.example.billingms.config;

import com.example.billingms.application.port.BillingSnapshotFinder;
import com.example.billingms.application.port.InvoiceNumbering;
import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.infrastructure.booking.RestBillingSnapshotFinder;
import com.example.billingms.infrastructure.persistence.InvoiceLineItemMapper;
import com.example.billingms.infrastructure.persistence.InvoiceMapper;
import com.example.billingms.infrastructure.persistence.MyBatisInvoiceRepository;
import com.example.billingms.infrastructure.persistence.SequenceInvoiceNumbering;
import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class BillingConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
     *
     * <p>認可を書き忘れたエンドポイントが無認証で開くことを、認可判定より前で塞ぐ。
     * 公開エンドポイントを持たないため、除外はヘルスチェックだけである。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration =
                new FilterRegistrationBean<>(new AuthenticatedUserFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * 業務タイムゾーンの時計。
     *
     * <p><strong>UTC で「今日」を決めない。</strong>請求番号の年も、発行日時も、
     * 業務の暦で決まる——UTC だと年末年始の数時間だけ前年の番号が出る。
     */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    /**
     * 料金算出の入力を bookingms へ取りに行く ACL（[ADR-027] 決定 7）。
     *
     * <p><strong>利用者ヘッダは伝播しない。</strong>この呼び出しは「システムが料金の入力を
     * 引く」ものであり、利用者の代理ではない。名乗りはする——名乗らないと相手の
     * [ADR-007] フィルタが一律に断る。
     */
    @Bean
    public BillingSnapshotFinder billingSnapshotFinder(
            @Value("${app.booking-service.base-url}") String baseUrl) {
        return new RestBillingSnapshotFinder(
                RestClient.builder().baseUrl(baseUrl)
                        .requestFactory(bookingRequestFactory()).build());
    }

    /**
     * bookingms への呼び出しに期限を置く。
     *
     * <p><strong>「落ちている」と「遅い」は別の障害である</strong>（bookingms → routingms で
     * 同じ判断をしている）。応答が返らないだけの状態でスレッドが埋まると、精算と無関係な
     * 一覧まで巻き込んで止まる。
     *
     * <p><strong>再送はしない。</strong>遅い相手に送り直すと詰まりが増える。
     */
    private static ClientHttpRequestFactory bookingRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @Bean
    public InvoiceRepository invoiceRepository(InvoiceMapper invoices,
            InvoiceLineItemMapper lineItems) {
        return new MyBatisInvoiceRepository(invoices, lineItems);
    }

    @Bean
    public InvoiceNumbering invoiceNumbering(InvoiceMapper invoices, Clock clock) {
        return new SequenceInvoiceNumbering(invoices, clock);
    }

    /**
     * 予約に精算の完了を知らせる ACL（US23-4・[ADR-028] 決定 1）。
     *
     * <p><strong>読み取りと同じ相手・同じ名乗りだが、こちらは副作用を持つ。</strong>
     * 本 IT で増えた 1 本である。
     */
    @Bean
    public com.example.billingms.application.port.BookingSettlementNotifier
            bookingSettlementNotifier(@Value("${app.booking-service.base-url}") String baseUrl) {
        return new com.example.billingms.infrastructure.booking.RestBookingSettlementNotifier(
                RestClient.builder().baseUrl(baseUrl)
                        .requestFactory(bookingRequestFactory()).build());
    }
}
