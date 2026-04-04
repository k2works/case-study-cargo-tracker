package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.infrastructure.repositories.VoyageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("VoyageRepository 統合テスト")
class VoyageRepositoryTest {

    @Autowired
    private VoyageRepository voyageRepository;

    @Test
    @DisplayName("出発地と目的地で直行便の航海を検索できる")
    void searchDirectVoyages_直行便() {
        List<Voyage> results = voyageRepository.searchVoyages("JPTYO", "SGSIN");

        assertThat(results).isNotEmpty();
        // SG001, SG002, SG003 が返る（SG002 は経由便だが JPTYO→...→SGSIN を網羅）
        List<Voyage> directVoyages = results.stream()
            .filter(v -> v.legs().stream()
                .anyMatch(l -> l.originLocode().equals("JPTYO") && l.destinationLocode().equals("SGSIN")))
            .toList();
        assertThat(directVoyages).isNotEmpty();
    }

    @Test
    @DisplayName("対応貨物種別でフィルタリングできる")
    void searchVoyages_byCargoType() {
        List<Voyage> results = voyageRepository.searchVoyages("JPTYO", "SGSIN");

        List<Voyage> refrigerated = results.stream()
            .filter(v -> v.supports(CargoType.REFRIGERATED))
            .toList();

        assertThat(refrigerated).isNotEmpty();
        refrigerated.forEach(v ->
            assertThat(v.supportedCargoTypes()).contains(CargoType.REFRIGERATED)
        );
    }

    @Test
    @DisplayName("存在しない出発地・目的地の場合は空リストを返す")
    void searchVoyages_存在しない経路() {
        List<Voyage> results = voyageRepository.searchVoyages("ZZZXX", "YYYYY");
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("航海番号で 1 件取得できる")
    void findByVoyageNumber_存在する() {
        var voyage = voyageRepository.findByVoyageNumber("SG001");
        assertThat(voyage).isPresent();
        assertThat(voyage.get().voyageNumber()).isEqualTo("SG001");
        assertThat(voyage.get().legs()).isNotEmpty();
    }

    @Test
    @DisplayName("存在しない航海番号の場合は空の Optional を返す")
    void findByVoyageNumber_存在しない() {
        var voyage = voyageRepository.findByVoyageNumber("NOTEXIST");
        assertThat(voyage).isEmpty();
    }
}
