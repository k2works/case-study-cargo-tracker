package com.example.routingms.domain.services;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.projections.VoyageProjection;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 経路候補算出ドメインサービス（US08）のユニットテスト。
 *
 * <p>{@link OptimalRouteService} は航海スケジュール（Voyage Read Model）と
 * 探索制約（出発地・目的地・到着期限・貨物種別）を入力に、寄港地の接続を評価して
 * 経路候補を推奨順に算出する純粋なドメインサービスである。外部依存を持たないため
 * モックを使わず、手で構築した航海一覧で振る舞いを検証する。</p>
 */
class OptimalRouteServiceTest {

    private final OptimalRouteService service = new OptimalRouteService();

    private static VoyageProjection voyage(
            String voyageNumber, String origin, String destination,
            LocalDateTime departure, LocalDateTime arrival, List<String> cargoTypes) {
        VoyageProjection v = new VoyageProjection();
        v.setVoyageNumber(voyageNumber);
        v.setOriginUnlocode(origin);
        v.setDestUnlocode(destination);
        v.setDepartureDate(departure);
        v.setArrivalDate(arrival);
        v.setStatus("SCHEDULED");
        v.setAcceptedCargoTypes(cargoTypes);
        return v;
    }

    @Test
    void 直行便を最優先候補として算出する() {
        // Given: 直行便（JPTYO→DEHAM）と乗り継ぎ便（JPTYO→SGSIN→DEHAM）
        VoyageProjection direct = voyage("V-DIRECT", "JPTYO", "DEHAM",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        VoyageProjection legA = voyage("V-A", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0), List.of("GENERAL"));
        VoyageProjection legB = voyage("V-B", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(legA, legB, direct));

        // Then: 直行便が先頭・推奨で、乗り継ぎ便も候補に含まれる
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).isDirect()).isTrue();
        assertThat(candidates.get(0).voyageNumbers()).containsExactly("V-DIRECT");
        assertThat(candidates.get(1).isDirect()).isFalse();
        assertThat(candidates.get(1).voyageNumbers()).containsExactly("V-A", "V-B");
    }

    @Test
    void 寄港地で接続する乗り継ぎ候補を算出する() {
        // Given: 直行便はなく、SGSIN で接続する 2 便のみ
        VoyageProjection legA = voyage("V-A", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0), List.of("GENERAL"));
        VoyageProjection legB = voyage("V-B", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(legA, legB));

        // Then
        assertThat(candidates).hasSize(1);
        RouteCandidate candidate = candidates.get(0);
        assertThat(candidate.legs()).hasSize(2);
        assertThat(candidate.legs().get(0).loadUnlocode()).isEqualTo("JPTYO");
        assertThat(candidate.legs().get(0).unloadUnlocode()).isEqualTo("SGSIN");
        assertThat(candidate.legs().get(1).loadUnlocode()).isEqualTo("SGSIN");
        assertThat(candidate.legs().get(1).unloadUnlocode()).isEqualTo("DEHAM");
    }

    @Test
    void 乗り継ぎ便が接続便の到着前に出発する組み合わせは候補にしない() {
        // Given: legB が legA の到着前に出発（接続不可）
        VoyageProjection legA = voyage("V-A", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0), List.of("GENERAL"));
        VoyageProjection legB = voyage("V-B", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 8, 9, 0), LocalDateTime.of(2026, 7, 25, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(legA, legB));

        // Then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 乗り継ぎ便が接続便の到着と同時刻に出発する組み合わせは候補にする() {
        // Given: legB が legA の到着時刻ちょうどに出発（境界：到着 == 出発）
        VoyageProjection legA = voyage("V-A", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0), List.of("GENERAL"));
        VoyageProjection legB = voyage("V-B", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 10, 18, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(legA, legB));

        // Then: 到着と同時刻の出発は接続可能（connects は !isBefore のため等値を含む。境界を固定）
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).voyageNumbers()).containsExactly("V-A", "V-B");
    }

    @Test
    void 貨物種別を受け入れない航海は候補から除外する() {
        // Given: 危険物を受け入れない直行便
        VoyageProjection direct = voyage("V-DIRECT", "JPTYO", "DEHAM",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "HAZARDOUS");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(direct));

        // Then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 対応貨物種別が未登録の航海は一般貨物のみ受け入れる() {
        // Given: acceptedCargoTypes が空 → 一般貨物のみ受け入れる（Voyage 集約の不変条件）
        VoyageProjection direct = voyage("V-DIRECT", "JPTYO", "DEHAM",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of());
        RouteSearchSpecification generalSpec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");
        RouteSearchSpecification reeferSpec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "REFRIGERATED");

        // When / Then
        assertThat(service.calculate(generalSpec, List.of(direct))).hasSize(1);
        assertThat(service.calculate(reeferSpec, List.of(direct))).isEmpty();
    }

    @Test
    void 到着期限を超過する経路は候補から除外する() {
        // Given: 期限（2026-07-20）を超えて到着する直行便
        VoyageProjection direct = voyage("V-LATE", "JPTYO", "DEHAM",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 7, 20), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(direct));

        // Then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 期限内に到達可能な経路がない場合は空リストを返す() {
        // Given: 出発地・目的地に合致する航海が存在しない
        VoyageProjection unrelated = voyage("V-OTHER", "USNYC", "GBLON",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 20, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(unrelated));

        // Then
        assertThat(candidates).isEmpty();
    }

    @Test
    void 概算費用を区間数と所要日数から算出する() {
        // Given: 直行便（25 日）と乗り継ぎ便（27 日）
        VoyageProjection direct = voyage("V-DIRECT", "JPTYO", "DEHAM",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        VoyageProjection legA = voyage("V-A", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 10, 18, 0), List.of("GENERAL"));
        VoyageProjection legB = voyage("V-B", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 1), "GENERAL");

        // When
        List<RouteCandidate> candidates = service.calculate(spec, List.of(legA, legB, direct));

        // Then: 直行 = 1 区間 × 200,000 + 25 日 × 40,000 = 1,200,000
        RouteCandidate directCandidate = candidates.get(0);
        assertThat(directCandidate.estimatedCost()).isEqualByComparingTo("1200000");
        assertThat(directCandidate.currency()).isEqualTo("JPY");
        // 乗り継ぎ = 2 区間 × 200,000 + 27 日 × 40,000 = 1,480,000
        RouteCandidate transship = candidates.get(1);
        assertThat(transship.estimatedCost()).isEqualByComparingTo("1480000");
    }

    @Test
    void 候補を推奨順に並べる_直行を最優先し次に所要日数の短い順() {
        // Given: 直行（25日）/ 乗り継ぎ短（27日）/ 乗り継ぎ長（35日）
        VoyageProjection direct = voyage("V-DIRECT", "JPTYO", "DEHAM",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0), List.of("GENERAL"));
        VoyageProjection shortA = voyage("V-SA", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 8, 18, 0), List.of("GENERAL"));
        VoyageProjection shortB = voyage("V-SB", "SGSIN", "DEHAM",
                LocalDateTime.of(2026, 7, 9, 9, 0), LocalDateTime.of(2026, 7, 30, 18, 0), List.of("GENERAL"));
        VoyageProjection longA = voyage("V-LA", "JPTYO", "AEJEA",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 15, 18, 0), List.of("GENERAL"));
        VoyageProjection longB = voyage("V-LB", "AEJEA", "DEHAM",
                LocalDateTime.of(2026, 7, 20, 9, 0), LocalDateTime.of(2026, 8, 7, 18, 0), List.of("GENERAL"));
        RouteSearchSpecification spec =
                new RouteSearchSpecification("JPTYO", "DEHAM", LocalDate.of(2026, 8, 10), "GENERAL");

        // When
        List<RouteCandidate> candidates =
                service.calculate(spec, List.of(longA, longB, shortA, shortB, direct));

        // Then: 直行が先頭、続いて所要日数の短い乗り継ぎ
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).voyageNumbers()).containsExactly("V-DIRECT");
        assertThat(candidates.get(1).voyageNumbers()).containsExactly("V-SA", "V-SB");
        assertThat(candidates.get(2).voyageNumbers()).containsExactly("V-LA", "V-LB");
        assertThat(candidates.get(0).estimatedDays()).isEqualTo(25);
    }
}
