package com.example.cargotracker.routing.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.events.VoyageCancelledEvent;
import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.infrastructure.projection.VoyageProjection;
import com.example.cargotracker.shared.contract.query.FindRouteCandidatesQuery;
import com.example.cargotracker.shared.contract.query.RouteCandidateDto;
import com.example.cargotracker.shared.contract.query.RouteCandidatesResponse;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 経路候補の問い合わせを実 DB で固定する（US08）。
 *
 * <p>探索の判断そのものは {@code RouteSearchServiceTest} が見る。ここで見るのは
 * <b>投影からグラフを組む部分</b>（キャンセル済み・出港済みを外す、受入種別の既定）。
 * 単体では組み立て方を確かめられない。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RouteCandidateQueryIT extends AbstractAxonIntegrationTest {

    @Autowired
    private VoyageProjection projection;

    @Autowired
    private RoutingQueryHandler queries;

    @Autowired
    private java.time.Clock clock;

    /** 航海番号は 20 文字まで。 */
    private static String uniqueNumber(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36);
    }

    /** 「今」より後に出る航海にする。過去の便を候補に出さないのが既定だから。 */
    private Instant future(int days) {
        return clock.instant().plusSeconds(days * 86400L);
    }

    private void register(String number, String from, String to, int departInDays,
            int arriveInDays, List<String> cargoTypes) {
        projection.on(new VoyageRegisteredEvent(number, "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement(from, to,
                        future(departInDays), future(arriveInDays))),
                cargoTypes, "routing01"));
    }

    /** 2 区間の航海。前半が出発済みでも、後半にはまだ積める。 */
    private void registerTwoLegs(String number, String from, String via, String to,
            int firstDepart, int firstArrive, int secondDepart, int secondArrive) {
        projection.on(new VoyageRegisteredEvent(number, "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement(from, via,
                                future(firstDepart), future(firstArrive)),
                        new VoyageRegisteredEvent.Movement(via, to,
                                future(secondDepart), future(secondArrive))),
                List.of("GENERAL"), "routing01"));
    }

    private FindRouteCandidatesQuery query(String from, String to, String cargoType,
            int deadlineInDays) {
        return new FindRouteCandidatesQuery(from, to,
                LocalDate.ofInstant(future(deadlineInDays), clock.getZone()),
                cargoType, List.of(), null);
    }

    @Test
    @DisplayName("投影から候補が出る（区間・航海番号・所要日数・直行の別）")
    void findsCandidatesFromProjection() {
        String number = uniqueNumber("V-OK-");
        register(number, "JPTYO", "USNYC", 2, 16, List.of("GENERAL"));

        RouteCandidatesResponse response = queries.handle(query("JPTYO", "USNYC", "GENERAL", 30));

        assertThat(response.candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.direct()).isTrue();
                    assertThat(candidate.transitDays()).isEqualTo(14);
                    RouteCandidateDto.LegDto leg = candidate.legs().get(0);
                    assertThat(leg.loadUnLocode()).isEqualTo("JPTYO");
                    assertThat(leg.unloadUnLocode()).isEqualTo("USNYC");
                });
        assertThat(response.truncated()).isFalse();
    }

    @Test
    @DisplayName("キャンセルした航海は候補に出ない（走らない船で経路を組ませない）")
    void excludesCancelledVoyages() {
        String number = uniqueNumber("V-CX-");
        register(number, "JPTYO", "NLRTM", 2, 16, List.of("GENERAL"));
        assertThat(queries.handle(query("JPTYO", "NLRTM", "GENERAL", 30)).candidates())
                .isNotEmpty();

        projection.on(new VoyageCancelledEvent(number, "運航中止", "routing01"));

        assertThat(queries.handle(query("JPTYO", "NLRTM", "GENERAL", 30)).candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .isEmpty();
    }

    @Test
    @DisplayName("対応しない貨物種別の航海は候補に出ない")
    void excludesVoyagesThatRejectTheCargoType() {
        String number = uniqueNumber("V-GN-");
        register(number, "JPTYO", "DEHAM", 2, 16, List.of("GENERAL"));

        assertThat(queries.handle(query("JPTYO", "DEHAM", "HAZARDOUS", 30)).candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .isEmpty();
    }

    @Test
    @DisplayName("種別を選ばなかった航海は一般貨物として扱う（不変条件 4 の既定）")
    void defaultsToGeneralWhenNoCargoTypeRecorded() {
        String number = uniqueNumber("V-DF-");
        // 集約は空なら GENERAL を書くが、投影に 1 行も無い状態でも既定は同じ。
        register(number, "JPTYO", "GBLON", 2, 16, List.of());

        assertThat(queries.handle(query("JPTYO", "GBLON", "GENERAL", 30)).candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .hasSize(1);
    }

    @Test
    @DisplayName("期限に間に合わない便しか無いときは候補 0 件（例外にしない）")
    void returnsEmptyWhenNothingMeetsTheDeadline() {
        String number = uniqueNumber("V-LT-");
        register(number, "JPTYO", "FRPAR", 2, 40, List.of("GENERAL"));

        RouteCandidatesResponse response = queries.handle(query("JPTYO", "FRPAR", "GENERAL", 10));

        assertThat(response.candidates()).isEmpty();
        assertThat(response.truncated()).as("便が無いのは打ち切りではない").isFalse();
    }

    @Test
    @DisplayName("知らない貨物種別は断る（黙って 0 件にしない）")
    void rejectsUnknownCargoType() {
        assertThatThrownBy(() -> queries.handle(
                new FindRouteCandidatesQuery("JPTYO", "USNYC", LocalDate.of(2026, 12, 1),
                        "UNKNOWN", List.of(), null)))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("貨物種別が空のときも断る（NPE を業務の断りに化けさせない）")
    void rejectsMissingCargoType() {
        assertThatThrownBy(() -> queries.handle(
                new FindRouteCandidatesQuery("JPTYO", "USNYC", LocalDate.of(2026, 12, 1),
                        null, List.of(), null)))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("出発済みの区間は候補に出ない（走ってしまった便に積ませない）")
    void excludesDepartedMovements() {
        String number = uniqueNumber("V-PS-");
        // 出発が過去。到着はまだ先。
        register(number, "JPTYO", "USNYC", -2, 16, List.of("GENERAL"));

        assertThat(queries.handle(query("JPTYO", "USNYC", "GENERAL", 30)).candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .isEmpty();
    }

    @Test
    @DisplayName("航海が出港済みでも、まだ出ていない後半の区間には積める")
    void keepsLaterMovementsOfDepartedVoyage() {
        String number = uniqueNumber("V-HF-");
        // JPTYO → SGSIN は出発済み。SGSIN → USNYC はこれから。
        registerTwoLegs(number, "JPTYO", "SGSIN", "USNYC", -5, -1, 3, 20);

        // 前半（JPTYO 発）は候補に出ない。
        assertThat(queries.handle(query("JPTYO", "USNYC", "GENERAL", 40)).candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .isEmpty();
        // 後半（SGSIN 発）は候補に出る。航海の出港ではなく区間の出発で見るため。
        assertThat(queries.handle(query("SGSIN", "USNYC", "GENERAL", 40)).candidates())
                .filteredOn(c -> c.legs().get(0).voyageNumber().equals(number))
                .hasSize(1);
    }

    @Test
    @DisplayName("投影が知らない港は断る（黙って 0 件にしない）")
    void rejectsUnknownPort() {
        // 書式は正しいが、どの航海も通らない港。打ち間違いを「経路が無い」と
        // 読ませると、条件を変えても直らないものを変え続けることになる。
        register(uniqueNumber("V-KP-"), "JPTYO", "USNYC", 2, 16, List.of("GENERAL"));

        assertThatThrownBy(() -> queries.handle(query("JPTYO", "ZZZZZ", "GENERAL", 30)))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("ZZZZZ");
    }
}
