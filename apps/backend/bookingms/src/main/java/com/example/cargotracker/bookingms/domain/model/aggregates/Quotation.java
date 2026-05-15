package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.bookingms.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Money;
import com.example.cargotracker.bookingms.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 見積 Aggregate（US01 / UC01）。
 *
 * <p>Axon Framework 5.1 の Event Sourcing パターン（ADR-0007）で実装。
 * domain-model.md L1202 のトレーサビリティに準拠。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>同一 {@code quotationId} で 2 回目の作成は Axon イベントストアで重複検知</li>
 *   <li>有効期限は作成日から 7 日</li>
 *   <li>候補が 1 件以上ある場合 {@code OFFERED}、0 件の場合 {@code DRAFT}（受入条件 5）</li>
 * </ul>
 *
 * <p>料金算出ロジックは IT3 では集約内に閉じた仮実装とし、IT4 以降で
 * routingms からの実航海データを参照する {@code OptimalRouteService} に置換予定。</p>
 */
@EventSourced(idType = String.class, tagKey = "quotationId")
@Profile("!springboot-integration-test")
public final class Quotation {

    /** 見積有効期限（日数）。 */
    static final int VALIDITY_DAYS = 7;

    /** 仮の基本料金（JPY/kg）。IT4 で routingms から距離係数を取得して置換予定。 */
    private static final BigDecimal BASE_RATE_PER_KG = new BigDecimal("1000");

    /** 仮の所要日数（直行ルート）。 */
    private static final int DIRECT_ROUTE_DAYS = 14;

    private String quotationId;
    private ShipperId shipperId;
    private RouteSpecification routeSpec;
    private CargoType cargoType;
    private BigDecimal weightKg;
    private HazardInfo hazardInfo;
    private Money estimatedAmount;
    private List<RouteCandidate> candidates = new ArrayList<>();
    private LocalDate validUntil;
    private QuotationStatus status;

    @EntityCreator
    public Quotation() {
        // Axon が Event 再生で呼び出すデフォルトコンストラクタ。
    }

    /**
     * 見積作成（作成系コマンド）。
     *
     * <p>ADR-0007 推奨パターン: 作成系 Command は {@code static} メソッドとして実装し、
     * {@link EventAppender} を引数で受け取る。</p>
     *
     * <p>処理:</p>
     * <ol>
     *   <li>貨物種別係数と重量で概算料金を算出</li>
     *   <li>直行ルート候補を生成（IT3 仮実装。IT4 で複数候補に拡張）</li>
     *   <li>希望期限に間に合うかチェック（受入条件 5）。間に合わない候補は除外</li>
     *   <li>候補有無で status を OFFERED / DRAFT に分岐</li>
     * </ol>
     */
    @CommandHandler
    public static String create(CreateQuotationCommand command, EventAppender appender) {
        Money baseAmount = calculateBaseAmount(command.weightKg(), command.cargoType());
        List<RouteCandidate> candidates = generateCandidates(
                command.routeSpec(), baseAmount, command.weightKg(), command.cargoType());
        QuotationStatus status = candidates.isEmpty() ? QuotationStatus.DRAFT : QuotationStatus.OFFERED;
        LocalDate validUntil = LocalDate.now().plusDays(VALIDITY_DAYS);

        appender.append(new QuotationCreatedEvent(
                command.quotationId(),
                command.shipperId(),
                command.routeSpec(),
                command.cargoType(),
                command.weightKg(),
                command.hazardInfo(),
                baseAmount,
                List.copyOf(candidates),
                validUntil,
                status));
        return command.quotationId();
    }

    @EventSourcingHandler
    public void on(QuotationCreatedEvent event) {
        this.quotationId = event.quotationId();
        this.shipperId = event.shipperId();
        this.routeSpec = event.routeSpec();
        this.cargoType = event.cargoType();
        this.weightKg = event.weightKg();
        this.hazardInfo = event.hazardInfo();
        this.estimatedAmount = event.estimatedAmount();
        this.candidates = new ArrayList<>(event.candidates());
        this.validUntil = event.validUntil();
        this.status = event.status();
    }

    /** 基本料金 = 重量 × 単価 × 貨物種別係数。 */
    static Money calculateBaseAmount(BigDecimal weightKg, CargoType cargoType) {
        BigDecimal factor = cargoTypeFactor(cargoType);
        BigDecimal amount = weightKg.multiply(BASE_RATE_PER_KG).multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);
        return new Money(amount, "JPY");
    }

    private static BigDecimal cargoTypeFactor(CargoType cargoType) {
        return switch (cargoType) {
            case GENERAL -> BigDecimal.ONE;
            case HAZARDOUS, REFRIGERATED -> new BigDecimal("1.3");
        };
    }

    /**
     * ルート候補生成（IT3 仮実装）。
     *
     * <p>現状は直行ルート 1 件のみ。IT4 で routingms 連携により複数候補を扱う。
     * 希望期限に間に合わないルートは除外する（受入条件 5）。</p>
     */
    static List<RouteCandidate> generateCandidates(RouteSpecification routeSpec,
                                                   Money baseAmount,
                                                   BigDecimal weightKg,
                                                   CargoType cargoType) {
        List<RouteCandidate> result = new ArrayList<>();
        // 直行ルート候補（14 日想定）
        long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), routeSpec.arrivalDeadline());
        if (daysUntilDeadline >= DIRECT_ROUTE_DAYS) {
            String summary = routeSpec.origin().unLocode().value() + " → "
                    + routeSpec.destination().unLocode().value();
            result.add(new RouteCandidate(DIRECT_ROUTE_DAYS, baseAmount, summary, "TBD"));
        }
        return result;
    }

    public String getQuotationId() {
        return quotationId;
    }

    public ShipperId getShipperId() {
        return shipperId;
    }

    public RouteSpecification getRouteSpec() {
        return routeSpec;
    }

    public CargoType getCargoType() {
        return cargoType;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Money getEstimatedAmount() {
        return estimatedAmount;
    }

    public List<RouteCandidate> getCandidates() {
        return List.copyOf(candidates);
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public HazardInfo getHazardInfo() {
        return hazardInfo;
    }

    public QuotationStatus getStatus() {
        return status;
    }
}
