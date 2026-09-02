package com.example.bookingms.infrastructure.config;

import com.example.bookingms.domain.repository.BillableCargoFinder;
import com.example.bookingms.application.internal.outboundservices.acl.CargoEventNotifier;
import com.example.bookingms.domain.repository.CancellationRequestRepository;
import com.example.bookingms.domain.repository.LocationRepository;
import com.example.bookingms.application.internal.outboundservices.acl.RouteCandidateFinder;
import com.example.bookingms.infrastructure.repositories.BillableCargoMapper;
import com.example.bookingms.infrastructure.repositories.CancellationRequestMapper;
import com.example.bookingms.infrastructure.repositories.MyBatisBillableCargoFinder;
import com.example.bookingms.infrastructure.repositories.MyBatisCancellationRequestRepository;
import com.example.bookingms.infrastructure.acl.CargoEventChannels;
import com.example.bookingms.infrastructure.acl.RabbitCargoEventNotifier;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import com.example.bookingms.infrastructure.acl.RestRouteCandidateFinder;
import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BookingConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
     *
     * <p>bookingms に公開エンドポイントは無い。認可判定より前に弾くため、順序を最上位にする。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration =
                new FilterRegistrationBean<>(new AuthenticatedUserFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** 業務日付は業務タイムゾーンで判断する。UTC で判断すると「当日」の扱いがずれる時間帯ができる。 */
    @Bean
    public Clock clock(@Value("${app.business-time-zone:Asia/Tokyo}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }

    /**
     * 経路候補を取りに行く先（[ADR-019]）。
     *
     * <p><strong>接続先に既定値を持たせない。</strong>持たせると設定漏れが実行時まで
     * 表面化せず、しかも IT5 では既定値が bookingms 自身を指していたため「経路の確定だけが
     * 必ず失敗する」状態になっていた。値は {@code application.yml} と環境変数で与える。
     *
     * <p><strong>利用者ヘッダ（[ADR-007]）は伝播しない。</strong>この呼び出しは「システムが
     * 経路候補を引く」ものであり、利用者の代理ではない。伝播すると、bookingms の中で完結する
     * 処理（確定時の再検証）が呼び出し元のロールに依存する。
     */
    @Bean
    public RouteCandidateFinder routeCandidateFinder(
            @Value("${app.routing-service.base-url}") String baseUrl,
            LocationRepository locations) {
        return new RestRouteCandidateFinder(
                RestClient.builder().baseUrl(baseUrl).requestFactory(routingRequestFactory())
                        .build(),
                locations);
    }

    /**
     * routingms への呼び出しに期限を置く。
     *
     * <p><strong>「落ちている」と「遅い」は別の障害である。</strong>[ADR-019] のネガティブは
     * 「routingms が落ちていると確定できない」と書いたが、応答が返らないだけの状態では
     * bookingms のスレッドが確定 1 件につき 1 本ずつ埋まり、<strong>経路と無関係な予約一覧や
     * 荷主登録まで巻き込んで止まる</strong>。落ちる範囲を routingms に閉じるために期限を置く。
     *
     * <p><strong>再送はしない。</strong>再検証の遅さは確定操作の遅さに直結し、遅い相手に
     * 送り直すと詰まりが増える。
     */
    private static ClientHttpRequestFactory routingRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    /**
     * 料金の試算を billingms に問う ACL（US01-3・[ADR-028] 決定 6）。
     *
     * <p><strong>本 IT で増えた結合方向である</strong>——これまで bookingms → billingms は
     * 無かった。<strong>期限を置く</strong>のは routingms と同じ理由で、応答が返らない
     * だけの状態で見積 1 件につきスレッドが 1 本ずつ埋まると、見積と無関係な画面まで
     * 巻き込んで止まるからである。
     */
    @Bean
    public com.example.bookingms.application.internal.outboundservices.acl.ChargeQuoteFinder chargeQuoteFinder(
            @Value("${app.billing-service.base-url}") String baseUrl) {
        return new com.example.bookingms.infrastructure.acl.RestChargeQuoteFinder(
                RestClient.builder().baseUrl(baseUrl).requestFactory(routingRequestFactory())
                        .build());
    }

    /** 見積の永続化（US01）。 */
    @Bean
    public com.example.bookingms.domain.repository.EstimateRepository estimateRepository(
            com.example.bookingms.infrastructure.repositories.EstimateMapper estimates,
            Clock clock) {
        return new com.example.bookingms.infrastructure.repositories.MyBatisEstimateRepository(
                estimates, clock);
    }

    /**
     * 予約のイベントを流す先（[ADR-022]）。
     *
     * <p>交換機とキューはここで宣言する。<strong>手で作った環境にだけあると、新しい環境で
     * 黙って届かなくなる</strong>（送り手はエラーにならない）。
     */
    @Bean
    public CargoEventNotifier cargoEventNotifier(RabbitTemplate rabbitTemplate) {
        return new RabbitCargoEventNotifier(rabbitTemplate);
    }

    /**
     * 貨物イベントの交換機。
     *
     * <p><strong>行き場のないイベントを予備の交換機へ逃がす</strong>（[ADR-022] 決定 4）。
     * ルーティングキーの綴り違いや購読側の配線漏れでは、イベントはどのキューにも入らず
     * 黙って消え、発行側は成功を返す。デッドレターはこの形を守らない。
     */
    @Bean
    public TopicExchange cargoEventExchange() {
        return new TopicExchange(CargoEventChannels.EXCHANGE, true, false,
                Map.of("alternate-exchange", CargoEventChannels.UNROUTABLE_EXCHANGE));
    }

    /**
     * イベントを JSON で運ぶ。
     *
     * <p>既定の Java 直列化にすると、受け手が同じクラスを持っていることが前提になり、
     * サービスの独立性が消える（[ADR-022] 決定 3 の「知らない項目を無視する」も成り立たない）。
     */
    @Bean
    public MessageConverter cargoEventMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * 行き場のないイベントの受け皿（[ADR-022] 決定 4）。
     *
     * <p><strong>発行側と購読側の両方が同じ内容で宣言する。</strong>交換機の引数が食い違うと、
     * 後から接続したほうが PRECONDITION_FAILED で落ちる。宣言は冪等なので、両方が同じものを
     * 宣言しても構わない——片方だけに置くと、そのサービスが起動していない環境で受け皿が
     * 消える。
     */
    @Bean
    public FanoutExchange bookingUnroutableExchange() {
        return new FanoutExchange(CargoEventChannels.UNROUTABLE_EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingUnroutableQueue() {
        return new Queue(CargoEventChannels.UNROUTABLE_QUEUE, true);
    }

    @Bean
    public Binding bookingUnroutableBinding() {
        return BindingBuilder.bind(bookingUnroutableQueue()).to(bookingUnroutableExchange());
    }

    /**
     * 荷役の交換機（[ADR-025] 決定 1）。
     *
     * <p><strong>handlingms・trackingms と同じ引数で宣言する。</strong>交換機は Topic で
     * あり、<strong>キューと結びつけを足す操作は既存の宣言を変えない</strong>。既存環境で
     * 宣言し直せずに止まるのは<strong>引数</strong>を変えたときであり、購読者の追加は
     * それに当たらない。
     *
     * <p>引数が 1 つでも食い違うと {@code PRECONDITION_FAILED} で落ち、<strong>後続の
     * キュー宣言まで止まる</strong>。これは Testcontainers では出ず、kind で初めて出る。
     */
    @Bean
    public TopicExchange bookingCargoHandlingExchange() {
        return new TopicExchange(CargoEventChannels.HANDLING_EXCHANGE, true, false,
                Map.of("alternate-exchange", CargoEventChannels.UNROUTABLE_EXCHANGE));
    }

    /** 受け取れなかったイベントの行き先（[ADR-022] 決定 4）。 */
    @Bean
    public DirectExchange bookingDeadLetterExchange() {
        return new DirectExchange(CargoEventChannels.DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * 荷役のイベントを受け取るキュー。
     *
     * <p><strong>購読側ごとにキューを分ける。</strong>trackingms と共有すると、片方が
     * 読んだイベントをもう片方が受け取れない。
     */
    @Bean
    public Queue bookingHandlingActivityRegisteredQueue() {
        return new Queue(CargoEventChannels.HANDLING_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", CargoEventChannels.DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", CargoEventChannels.HANDLING_DEAD_LETTER_QUEUE));
    }

    @Bean
    public Queue bookingHandlingDeadLetterQueue() {
        return new Queue(CargoEventChannels.HANDLING_DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding bookingHandlingDeadLetterBinding() {
        return BindingBuilder.bind(bookingHandlingDeadLetterQueue())
                .to(bookingDeadLetterExchange())
                .with(CargoEventChannels.HANDLING_DEAD_LETTER_QUEUE);
    }

    @Bean
    public Binding bookingHandlingActivityRegisteredBinding() {
        return BindingBuilder.bind(bookingHandlingActivityRegisteredQueue())
                .to(bookingCargoHandlingExchange())
                .with(CargoEventChannels.HANDLING_ACTIVITY_REGISTERED);
    }

    @Bean
    public CancellationRequestRepository cancellationRequestRepository(
            CancellationRequestMapper mapper) {
        return new MyBatisCancellationRequestRepository(mapper);
    }

    /** 料金算出の対象を引く（US21・[ADR-027] 決定 7）。読み取り専用のクエリ側。 */
    @Bean
    public BillableCargoFinder billableCargoFinder(BillableCargoMapper mapper) {
        return new MyBatisBillableCargoFinder(mapper);
    }

}
