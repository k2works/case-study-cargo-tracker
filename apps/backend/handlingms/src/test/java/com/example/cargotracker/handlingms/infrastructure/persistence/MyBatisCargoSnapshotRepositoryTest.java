package com.example.cargotracker.handlingms.infrastructure.persistence;

import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MyBatisCargoSnapshotRepository のユニットテスト。
 *
 * <p>Mapper をモック化し、{@link CargoSnapshotRecord} → {@link CargoSnapshot} 変換ロジックを検証する。</p>
 */
@DisplayName("MyBatisCargoSnapshotRepository")
class MyBatisCargoSnapshotRepositoryTest {

    private CargoSnapshotMapper mapper;
    private MyBatisCargoSnapshotRepository repository;

    @BeforeEach
    void setUp() {
        mapper = mock(CargoSnapshotMapper.class);
        repository = new MyBatisCargoSnapshotRepository(mapper);
    }

    @Test
    @DisplayName("findByTrackingNumber: レコードが存在する場合 CargoSnapshot に変換される")
    void findByTrackingNumber_存在() {
        CargoSnapshotRecord snapshot = new CargoSnapshotRecord();
        snapshot.setBookingId("B-001");
        snapshot.setTrackingNumber("TRK-20260720-ABC12345");
        snapshot.setOriginUnlocode("JPTYO");
        snapshot.setDestinationUnlocode("DEHAM");
        snapshot.setCargoType("GENERAL");
        snapshot.setArrivalDeadline(LocalDate.of(2099, 12, 31));
        snapshot.setBookingStatus("TRACKING_ISSUED");
        when(mapper.findByTrackingNumber(anyString())).thenReturn(snapshot);

        Optional<CargoSnapshot> result = repository.findByTrackingNumber(
                new TrackingNumber("TRK-20260720-ABC12345"));

        assertThat(result).isPresent();
        CargoSnapshot snap = result.get();
        assertThat(snap.bookingId()).isEqualTo("B-001");
        assertThat(snap.origin().unLocode().value()).isEqualTo("JPTYO");
        assertThat(snap.destination().unLocode().value()).isEqualTo("DEHAM");
        assertThat(snap.cargoType()).isEqualTo("GENERAL");
    }

    @Test
    @DisplayName("findByTrackingNumber: レコードが存在しない場合 Optional.empty")
    void findByTrackingNumber_不在() {
        when(mapper.findByTrackingNumber(anyString())).thenReturn(null);

        Optional<CargoSnapshot> result = repository.findByTrackingNumber(
                new TrackingNumber("TRK-20260720-NOTFOUND"));

        assertThat(result).isEmpty();
    }
}
