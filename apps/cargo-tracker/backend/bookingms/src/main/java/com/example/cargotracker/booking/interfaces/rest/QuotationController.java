package com.example.cargotracker.booking.interfaces.rest;

import com.example.cargotracker.booking.application.port.RouteCandidateFinder;
import com.example.cargotracker.booking.application.port.RouteSearchRequest;
import com.example.cargotracker.booking.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationRates;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.booking.domain.service.QuotationEstimator;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.FindQuotationQuery;
import com.example.cargotracker.booking.infrastructure.query.BookingQueries.QuotationView;
import com.example.cargotracker.booking.interfaces.rest.dto.ShipperDtos.PendingResponse;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.infrastructure.axon.QueryDispatcher;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 輸送見積（S12・S13 / UC01・US01）。
 *
 * <p><b>候補は既にある経路探索を使う</b>（{@code RouteCandidateFinder} →
 * routingms）。見積のために新しい探索を作らない——同じ条件で違う候補が出ると、
 * 見積と予約で話が合わなくなる。</p>
 *
 * <p><b>候補 0 件でも見積は作る</b>（正典の不変条件）。「期限に間に合う経路が
 * ありません」も荷主に返すべき答えで、断ると営業担当者は何も伝えられない。</p>
 *
 * <p><b>探索が落ちているときは断る</b>（503）。候補 0 件の見積として保存すると、
 * 「間に合う経路が無い」と読まれて誤った判断に使われる——<b>0 件と「数えられ
 * なかった」は別物</b>である。</p>
 */
@RestController
@RequestMapping("/api/v1/booking/quotations")
public class QuotationController {

    private final CommandGateway commands;
    private final QueryDispatcher queries;
    private final RouteCandidateFinder routeCandidates;
    private final QuotationEstimator estimator;
    private final QuotationRates rates;

    public QuotationController(CommandGateway commands, QueryDispatcher queries,
            RouteCandidateFinder routeCandidates, QuotationEstimator estimator,
            QuotationRates rates) {
        this.commands = commands;
        this.queries = queries;
        this.routeCandidates = routeCandidates;
        this.estimator = estimator;
        this.rates = rates;
    }

    /**
     * 見積を作る（US01 §受入基準 1〜5）。
     *
     * <p><b>見積番号はサーバで採る。</b> 画面に採らせると、押し直しが二つ目の
     * 見積になる。<b>36 文字に収める</b>——列は {@code VARCHAR(36)} で、接頭辞 +
     * UUID をそのまま繋ぐとあふれ、投影だけが静かに退避される（IT13・IT14 で
     * 2 度踏んだ）。</p>
     */
    @PostMapping
    public ResponseEntity<CreatedView> create(
            @RequestHeader(value = "X-Auth-Username", required = false) String username,
            @Valid @RequestBody CreateQuotationRequest request) {
        RouteSpecification routeSpecification = new RouteSpecification(
                Location.of(request.originUnLocode()),
                Location.of(request.destinationUnLocode()),
                request.arrivalDeadline());
        CargoType cargoType = CargoType.valueOf(request.cargoType());
        Weight weight = new Weight(request.weightKg());

        // **既にある探索を使う。** 見積のために新しい経路探索を作らない。
        // 落ちていれば RouteSearchUnavailable が上がり、503 で断る——0 件の
        // 見積として保存すると「間に合う経路が無い」と読まれる。
        var found = routeCandidates.find(RouteSearchRequest.of(
                routeSpecification.origin(), routeSpecification.destination(),
                routeSpecification.arrivalDeadline(), cargoType));

        String quotationId = "Q-" + UUID.randomUUID().toString().replace("-", "");
        commands.sendAndWait(new CreateQuotationCommand(quotationId, routeSpecification,
                cargoType, weight, hazardousOf(request),
                estimator.estimate(found.candidates(), cargoType, weight, rates),
                username), String.class);
        return ResponseEntity.ok(new CreatedView(quotationId));
    }

    /**
     * 見積 1 件（S13）。
     *
     * <p><b>投影がまだなら 202。</b> 「作ったのに読めない」を「ありません」に
     * 化けさせない（他の画面と同じ形）。</p>
     */
    @GetMapping("/{quotationId}")
    public ResponseEntity<?> find(@PathVariable String quotationId) {
        QuotationView view = queries.query(new FindQuotationQuery(quotationId),
                QuotationView.class);
        if (view == null) {
            return ResponseEntity.accepted().body(new PendingResponse(quotationId,
                    "見積を受け付けました。反映までしばらくお待ちください"));
        }
        return ResponseEntity.ok(view);
    }

    private static HazardousDeclaration hazardousOf(CreateQuotationRequest request) {
        if (request.hazardousImoClass() == null && request.hazardousUnNumber() == null) {
            return null;
        }
        return new HazardousDeclaration(request.hazardousImoClass(),
                request.hazardousUnNumber());
    }

    /** 作った見積。画面はこの番号で S13 へ移る。 */
    public record CreatedView(String quotationId) {
    }

    /**
     * 見積の入力（US01 §受入基準 1）。
     *
     * <p>5 項目——出発地・目的地・希望期限・貨物種別・重量（正典の不変条件 1）。
     * 危険物のときだけ危険物申告が要る。</p>
     */
    public record CreateQuotationRequest(
            @NotBlank String originUnLocode,
            @NotBlank String destinationUnLocode,
            @NotNull LocalDate arrivalDeadline,
            @NotBlank String cargoType,
            @NotNull BigDecimal weightKg,
            String hazardousImoClass,
            String hazardousUnNumber) {
    }
}
