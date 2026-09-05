package com.example.cargotracker.routing.infrastructure.query;

import com.example.cargotracker.routing.infrastructure.persistence.VoyageMapper;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyageQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.FindVoyagesQuery;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.MovementView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageListView;
import com.example.cargotracker.routing.infrastructure.query.RoutingQueries.VoyageView;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitPath;
import com.example.cargotracker.routing.domain.service.RouteSearchService;
import com.example.cargotracker.shared.contract.query.FindRouteCandidatesQuery;
import com.example.cargotracker.shared.contract.query.RouteCandidateDto;
import com.example.cargotracker.shared.contract.query.RouteCandidatesResponse;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 航海の問い合わせ。読み取りモデルは投影テーブルだけを見る。 */
@Component
public class RoutingQueryHandler {

    private final VoyageMapper voyages;
    private final Clock clock;
    private final RouteSearchService routeSearch;

    public RoutingQueryHandler(VoyageMapper voyages, Clock clock) {
        this.voyages = voyages;
        this.clock = clock;
        // 期限は日付で比べる。どの時間帯で日付にするかは業務の時計が決める。
        this.routeSearch = new RouteSearchService(clock.getZone());
    }

    /**
     * 経路候補（US08）。<b>契約クエリ 1 本目</b>。bookingms から Query Bus 越しに届く。
     *
     * <p><b>候補 0 件を例外にしない。</b> 「期限内に着ける経路が無い」は業務として
     * 起こることで、失敗ではない。呼ぶ側が「候補が無い」と「探索できなかった（503）」を
     * 言い分けられるよう、0 件は正常な応答として返す。</p>
     *
     * <p>知らない港・種別は断る（422）。黙って 0 件にすると、条件の打ち間違いが
     * 「経路が無い」と読める。</p>
     */
    @QueryHandler
    public RouteCandidatesResponse handle(FindRouteCandidatesQuery query) {
        RouteSearchSpecification specification = toSpecification(query);
        ProjectionVoyageGraph graph = new ProjectionVoyageGraph(voyages, clock.instant());
        // 書いた保証は実装する。港は書式だけを見ていたので、登録の無い港が
        // 黙って候補 0 件になっていた（IT5 レビュー 高 2）。
        rejectUnknownPorts(specification, graph);

        RouteSearchService.RouteSearchResult result = routeSearch.search(specification, graph);

        return new RouteCandidatesResponse(
                result.candidates().stream()
                        .map(this::toDto)
                        .toList(),
                result.truncated());
    }

    /**
     * 投影が知らない港を断る。
     *
     * <p>除外港は見ない。「通したくない港」なので、登録が無くても条件として成り立つ。</p>
     */
    private static void rejectUnknownPorts(RouteSearchSpecification specification,
            ProjectionVoyageGraph graph) {
        Set<Location> ports = new java.util.LinkedHashSet<>();
        ports.add(specification.origin());
        ports.add(specification.destination());
        if (specification.departFrom() != null) {
            ports.add(specification.departFrom());
        }
        for (Location port : ports) {
            if (!graph.knowsPort(port)) {
                throw new BusinessRuleViolation(
                        "その港を通る航海が登録されていません: " + port.unLocode().value());
            }
        }
    }

    private static RouteSearchSpecification toSpecification(FindRouteCandidatesQuery query) {
        Set<Location> exclude = query.excludeUnLocodes().stream()
                .map(Location::of)
                .collect(Collectors.toUnmodifiableSet());
        return new RouteSearchSpecification(
                Location.of(query.originUnLocode()),
                Location.of(query.destinationUnLocode()),
                query.arrivalDeadline(),
                cargoTypeOf(query.cargoType()),
                exclude,
                query.departFromUnLocode() == null
                        ? null : Location.of(query.departFromUnLocode()));
    }

    /** 契約は文字列で運ぶ。自 BC の列挙型に組み直すのはここ（知らない値は断る）。 */
    private static CargoType cargoTypeOf(String name) {
        if (name == null || name.isBlank()) {
            // NullPointerException を捕まえない。捕まえると、この先で起きる
            // 別の NPE（実装の誤り）まで「知らない貨物種別」に化ける。
            throw new BusinessRuleViolation("貨物種別は必須です");
        }
        try {
            return CargoType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleViolation("知らない貨物種別です: " + name);
        }
    }

    private RouteCandidateDto toDto(TransitPath path) {
        // 所要日数は切り上げる。13 時間の航海を「0 日」と出すと、届かないように読める。
        long hours = path.totalDuration().toHours();
        int transitDays = (int) Math.max(1, Math.ceil(hours / 24.0));
        return new RouteCandidateDto(
                path.edges().stream().map(RoutingQueryHandler::toLegDto).toList(),
                transitDays,
                path.isDirect());
    }

    private static RouteCandidateDto.LegDto toLegDto(TransitEdge edge) {
        return new RouteCandidateDto.LegDto(
                edge.voyageNumber(),
                edge.load().unLocode().value(),
                edge.unload().unLocode().value(),
                edge.loadTime(),
                edge.unloadTime());
    }

    @QueryHandler
    public VoyageView handle(FindVoyageQuery query) {
        VoyageMapper.VoyageRow row = voyages.findByNumber(query.voyageNumber());
        return row == null ? null : toView(row);
    }

    @QueryHandler
    public VoyageListView handle(FindVoyagesQuery query) {
        int size = Math.clamp(query.size(), 1, 200);
        int offset = Math.max(query.page(), 0) * size;
        // 「出港済みを外す」の基準時刻はここで 1 度だけ決める。行ごとに now() を
        // 引くと、ページの途中で境界が動く。
        java.time.Instant now = clock.instant();
        // 条件は既定の絞り込みと組み合わせる。条件で置き換えると、出港済みの航海が
        // 検索結果にだけ戻る（一覧では外しているのに、絞り込むと出てくる）。
        return new VoyageListView(
                voyages.findAll(query.includeFinished(), query.criteria(), now, size, offset)
                        .stream().map(this::toView).toList(),
                voyages.countAll(query.includeFinished(), query.criteria(), now));
    }

    private VoyageView toView(VoyageMapper.VoyageRow row) {
        List<String> cargoTypes = voyages.findAcceptedCargoTypes(row.voyageNumber());
        List<MovementView> movements = voyages.findMovements(row.voyageNumber()).stream()
                .map(m -> new MovementView(m.movementSeq(), m.departureUnlocode(),
                        m.arrivalUnlocode(), m.departureAt(), m.arrivalAt()))
                .toList();
        return new VoyageView(
                row.voyageNumber(), row.carrierCode(), row.carrierName(), row.vesselName(),
                row.departureUnlocode(), row.arrivalUnlocode(), row.departureAt(),
                row.arrivalAt(), row.cancelled(), cargoTypes, movements,
                row.updatedAt(), row.updatedBy(),
                row.cancelledAt(), row.cancelReason(), row.cancelledBy());
    }
}
