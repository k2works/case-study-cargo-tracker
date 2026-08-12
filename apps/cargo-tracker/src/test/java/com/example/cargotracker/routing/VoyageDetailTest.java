package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.routing.application.internal.queryservices.VoyageQueryService;
import com.example.cargotracker.routing.domain.model.entities.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierName;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 航海詳細（IT3 レビュー M1 / IT4 返済枠 C1）。
 *
 * <p>一覧の 1 行には航海の端点しか収まらない。<strong>乗り継ぎ便では、
 * どの港に何時に着いて何時に出るかが分からないと経路を組めない。</strong>
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class VoyageDetailTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private VoyageRepository repository;

    @Autowired
    private VoyageQueryService queryService;

    private String 乗り継ぎ便を登録する() {
        String number = "V" + UUID.randomUUID().toString().substring(0, 8);
        repository.save(Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number),
                new VesselName("さくら丸"),
                new CarrierName("日本海運"),
                Schedule.of(List.of(
                        CarrierMovement.of(
                                Location.of("JPYOK"), Location.of("SGSIN"),
                                Instant.parse("2026-12-01T10:00:00Z"),
                                Instant.parse("2026-12-08T06:00:00Z")),
                        CarrierMovement.of(
                                Location.of("SGSIN"), Location.of("DEHAM"),
                                Instant.parse("2026-12-09T10:00:00Z"),
                                Instant.parse("2026-12-28T06:00:00Z")))),
                Set.of(RoutingCargoType.GENERAL, RoutingCargoType.HAZARDOUS),
                RoutingWeight.ofKilograms(new java.math.BigDecimal("100000")))));
        return number;
    }

    /** 全区間が順番どおりに読める。**順序が崩れると寄港地の並びが意味を失う。** */
    @Test
    void 全区間が出発順に並ぶ() {
        String number = 乗り継ぎ便を登録する();

        var detail = queryService.findDetail(number).orElseThrow();

        assertThat(detail.movements())
                .extracting(m -> m.departure() + "->" + m.arrival())
                .containsExactly("JPYOK->SGSIN", "SGSIN->DEHAM");
    }

    /** 港コードだけでなく名称も出る。**コードだけでは、どこの港か分からない。** */
    @Test
    void 区間に港の名称が付く() {
        String number = 乗り継ぎ便を登録する();

        var detail = queryService.findDetail(number).orElseThrow();

        assertThat(detail.movements()).first()
                .satisfies(m -> {
                    assertThat(m.departureName()).isEqualTo("横浜");
                    assertThat(m.arrivalName()).isEqualTo("シンガポール");
                });
    }

    /** 船名・運送会社・取扱貨物種別が読める（US24 で登録した内容）。 */
    @Test
    void 便を特定する情報が読める() {
        String number = 乗り継ぎ便を登録する();

        var detail = queryService.findDetail(number).orElseThrow();

        assertThat(detail.vesselName()).isEqualTo("さくら丸");
        assertThat(detail.carrierName()).isEqualTo("日本海運");
        assertThat(detail.cargoTypeLabels()).containsExactlyInAnyOrder("一般貨物", "危険物");
    }

    /** 存在しない航海番号は 404。**URL を直接編集しただけで 500 にしない。** */
    @Test
    void 存在しない航海番号は404になる() throws Exception {
        mockMvc.perform(get("/voyages/{number}", "V-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }

    /** 画面に全区間が出る。 */
    @Test
    void 画面に寄港地の発着が表示される() throws Exception {
        String number = 乗り継ぎ便を登録する();

        mockMvc.perform(get("/voyages/{number}", number))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("シンガポール")))
                .andExpect(content().string(Matchers.containsString("ハンブルク")));
    }

    /** 経路設計者以外は URL 直打ちでも開けない。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は開けない() throws Exception {
        String number = 乗り継ぎ便を登録する();

        mockMvc.perform(get("/voyages/{number}", number))
                .andExpect(status().isForbidden());
    }
}
