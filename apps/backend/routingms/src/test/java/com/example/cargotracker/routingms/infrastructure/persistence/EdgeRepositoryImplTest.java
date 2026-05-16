package com.example.cargotracker.routingms.infrastructure.persistence;

import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.ports.EdgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EdgeRepositoryImpl 統合テスト（US08）。
 *
 * <p>carrier_movement + voyage_accepted_cargo_type を JOIN して TransitEdge を生成できることを検証する。</p>
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@Transactional
class EdgeRepositoryImplTest {

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private VoyageMapper voyageMapper;

    @BeforeEach
    void setUp() {
        // voyage + carrier_movement + accepted_cargo_type をセットアップ
        VoyageRecord voyage = new VoyageRecord();
        voyage.setVoyageNumber("V-TEST01");
        voyage.setCarrierCode("YMT");
        voyage.setCarrierName("Yamamoto Lines");
        voyage.setShipName("MV Test");
        voyage.setDepartureDate(LocalDateTime.of(2099, 7, 1, 9, 0));
        voyage.setArrivalDate(LocalDateTime.of(2099, 7, 15, 18, 0));
        voyage.setOriginUnlocode("JPYOK");
        voyage.setDestinationUnlocode("USLAX");
        voyage.setStatus("SCHEDULED");
        voyageMapper.insertVoyage(voyage);

        CarrierMovementRecord movement = new CarrierMovementRecord();
        movement.setVoyageNumber("V-TEST01");
        movement.setMovementSeq(1);
        movement.setDepartureUnlocode("JPYOK");
        movement.setArrivalUnlocode("USLAX");
        movement.setDepartureTime(LocalDateTime.of(2099, 7, 1, 9, 0));
        movement.setArrivalTime(LocalDateTime.of(2099, 7, 15, 18, 0));
        voyageMapper.insertCarrierMovement(movement);

        voyageMapper.insertAcceptedCargoType("V-TEST01", "GENERAL");
        voyageMapper.insertAcceptedCargoType("V-TEST01", "REFRIGERATED");
    }

    @Test
    void findAll_は_carrier_movementとcargo_typeをTransitEdgeに変換して返す() {
        List<TransitEdge> edges = edgeRepository.findAll();

        assertThat(edges).isNotEmpty();
        TransitEdge edge = edges.stream()
                .filter(e -> e.voyageNumber().equals("V-TEST01"))
                .findFirst()
                .orElseThrow();

        assertThat(edge.fromUnLocode().value()).isEqualTo("JPYOK");
        assertThat(edge.toUnLocode().value()).isEqualTo("USLAX");
        assertThat(edge.departureTime()).isEqualTo(LocalDateTime.of(2099, 7, 1, 9, 0));
        assertThat(edge.arrivalTime()).isEqualTo(LocalDateTime.of(2099, 7, 15, 18, 0));
        assertThat(edge.acceptedCargoTypes())
                .containsExactlyInAnyOrder(CargoType.GENERAL, CargoType.REFRIGERATED);
    }
}
