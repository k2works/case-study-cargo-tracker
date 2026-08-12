package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageView;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierName;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/** US07: 航海スケジュールを検索する。受け入れ基準に 1:1 で対応させる。 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class VoyageSearchTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private VoyageRepository repository;

    @Autowired
    private VoyageQueryService queryService;

    private String 航海を登録する(
            String origin, String destination, String departure, String arrival,
            Set<RoutingCargoType> types) {
        String number = "V" + UUID.randomUUID().toString().substring(0, 8);
        repository.save(Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number),
                new VesselName("さくら丸"),
                new CarrierName("日本海運"),
                Schedule.of(List.of(CarrierMovement.of(
                        Location.of(origin), Location.of(destination),
                        Instant.parse(departure), Instant.parse(arrival)))),
                types,
                RoutingWeight.ofKilograms(new java.math.BigDecimal("100000")))));
        return number;
    }

    private List<String> 番号(List<VoyageView> views) {
        return views.stream().map(VoyageView::voyageNumber).toList();
    }

    /** 受入基準: 出発地・目的地で絞り込める。 */
    @Test
    void 出発地と目的地で絞り込める() {
        String matching = 航海を登録する("JPOSA", "USLAX",
                "2026-10-01T10:00:00Z", "2026-10-14T06:00:00Z", Set.of(RoutingCargoType.GENERAL));
        String other = 航海を登録する("JPYOK", "DEHAM",
                "2026-10-01T10:00:00Z", "2026-10-30T06:00:00Z", Set.of(RoutingCargoType.GENERAL));

        var page = queryService.search("JPOSA", "USLAX", null, null, null, PageRequest.of(1));

        assertThat(番号(page.items())).contains(matching).doesNotContain(other);
    }

    /** 受入基準: 出発期間で絞り込める。 */
    @Test
    void 出発期間で絞り込める() {
        String withinRange = 航海を登録する("CNSHA", "USSEA",
                "2026-11-10T10:00:00Z", "2026-11-24T06:00:00Z", Set.of(RoutingCargoType.GENERAL));
        String outsideRange = 航海を登録する("CNSHA", "USSEA",
                "2026-12-10T10:00:00Z", "2026-12-24T06:00:00Z", Set.of(RoutingCargoType.GENERAL));

        var page = queryService.search("CNSHA", "USSEA",
                LocalDate.of(2026, java.time.Month.NOVEMBER, 1), LocalDate.of(2026, java.time.Month.NOVEMBER, 30), null, PageRequest.of(1));

        assertThat(番号(page.items())).contains(withinRange).doesNotContain(outsideRange);
    }

    /**
     * 境界。<strong>指定した日に出る便が漏れない。</strong>
     *
     * <p>上限をその日の 0 時として扱うと、**その日に出発する便がまるごと検索から消える。**
     */
    @Test
    void 出発期間の上限に指定した日の便も含まれる() {
        // 日本時間の 2026-11-30 23:00（UTC では 11-30 14:00）
        String lateOnLastDay = 航海を登録する("KRPUS", "USOAK",
                "2026-11-30T14:00:00Z", "2026-12-14T06:00:00Z", Set.of(RoutingCargoType.GENERAL));

        var page = queryService.search("KRPUS", "USOAK",
                null, LocalDate.of(2026, java.time.Month.NOVEMBER, 30), null, PageRequest.of(1));

        assertThat(番号(page.items()))
                .as("上限日の便が漏れると、その日に出る船を探せない")
                .contains(lateOnLastDay);
    }

    /** 受入基準: 危険物・冷凍貨物の場合、対応可能な航海のみに絞り込まれる。 */
    @Test
    void 対応可能な貨物種別で絞り込める() {
        String hazardousCapable = 航海を登録する("JPKOB", "NLRTM",
                "2026-10-05T10:00:00Z", "2026-11-05T06:00:00Z",
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.HAZARDOUS));
        String generalOnly = 航海を登録する("JPKOB", "NLRTM",
                "2026-10-06T10:00:00Z", "2026-11-06T06:00:00Z",
                Set.of(RoutingCargoType.GENERAL));

        var page = queryService.search("JPKOB", "NLRTM", null, null,
                RoutingCargoType.HAZARDOUS, PageRequest.of(1));

        assertThat(番号(page.items()))
                .as("危険物を扱えない航海が候補に出ると、積めない船を提案してしまう")
                .contains(hazardousCapable).doesNotContain(generalOnly);
    }

    /**
     * 貨物種別の判定が部分一致で誤検出しない。
     *
     * <p>カンマ区切りの文字列を素朴に LIKE すると、**GENERAL の検索で
     * REFRIGERATED を含む航海まで拾いかねない**（含まれる文字列の重なり）。
     */
    @Test
    void 貨物種別の判定が部分一致で誤検出しない() {
        String refrigeratedOnly = 航海を登録する("JPNGO", "AUSYD",
                "2026-10-07T10:00:00Z", "2026-10-27T06:00:00Z",
                Set.of(RoutingCargoType.REFRIGERATED));

        var page = queryService.search("JPNGO", "AUSYD", null, null,
                RoutingCargoType.GENERAL, PageRequest.of(1));

        assertThat(番号(page.items())).doesNotContain(refrigeratedOnly);
    }

    /** 受入基準: 条件を満たす航海がなければ、その旨が表示され条件を緩められる。 */
    @Test
    void 条件に一致しなければその旨が表示される() throws Exception {
        mockMvc.perform(get("/voyages")
                        .param("origin", "MXZLO").param("destination", "CLVAP"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("条件に一致する航海がありません")));
    }

    /** 直行便かどうかが一覧で分かる。**経路設計で最初に見る情報である。** */
    @Test
    void 直行便かどうかが分かる() {
        String directVoyage = 航海を登録する("JPHKT", "TWKHH",
                "2026-10-08T10:00:00Z", "2026-10-10T06:00:00Z", Set.of(RoutingCargoType.GENERAL));

        var page = queryService.search("JPHKT", "TWKHH", null, null, null, PageRequest.of(1));

        assertThat(page.items())
                .filteredOn(v -> v.voyageNumber().equals(directVoyage))
                .singleElement()
                .satisfies(v -> assertThat(v.isDirect()).isTrue());
    }

    /** 港の名称が出る。**コードだけでは、どこの港か分からない。** */
    @Test
    void 港の名称が表示される() {
        航海を登録する("JPTYO", "USNYC",
                "2026-10-09T10:00:00Z", "2026-11-09T06:00:00Z", Set.of(RoutingCargoType.GENERAL));

        var page = queryService.search("JPTYO", "USNYC", null, null, null, PageRequest.of(1));

        assertThat(page.items()).first()
                .satisfies(v -> {
                    assertThat(v.originName()).isEqualTo("東京");
                    assertThat(v.destinationName()).isEqualTo("ニューヨーク");
                });
    }

    /** 不正な貨物種別を URL に入れても 500 にしない。 */
    @Test
    void 不正な貨物種別を指定しても500にならない() throws Exception {
        mockMvc.perform(get("/voyages").param("cargoType", "NOT_A_TYPE"))
                .andExpect(status().isOk());
    }
}
