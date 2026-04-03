package com.example.cargotracker.exception.infrastructure;

import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.repository.CargoExceptionRepository;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CargoExceptionRepository 統合テスト")
class CargoExceptionRepositoryTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private CargoExceptionRepository cargoExceptionRepository;

    @Test
    @DisplayName("貨物例外を保存して追跡番号で取得できる")
    void saveAndFindByTrackingNumber() {
        ExceptionId id = ExceptionId.generate();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 28, 10, 0);

        CargoIncident exception = CargoIncident.create(
                id, "TRK-AB123456", ExceptionType.DELAY, "JPTYO", occurredAt, "悪天候"
        );
        cargoExceptionRepository.save(exception);

        List<CargoIncident> found = cargoExceptionRepository.findByTrackingNumber("TRK-AB123456");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(id);
        assertThat(found.get(0).getTrackingNumber()).isEqualTo("TRK-AB123456");
        assertThat(found.get(0).getExceptionType()).isEqualTo(ExceptionType.DELAY);
        assertThat(found.get(0).getLocationCode()).isEqualTo("JPTYO");
        assertThat(found.get(0).getReason()).isEqualTo("悪天候");
        assertThat(found.get(0).isUrgent()).isFalse();
        assertThat(found.get(0).getResolution()).isNull();
    }

    @Test
    @DisplayName("紛失例外は urgent フラグが保存・復元される")
    void save_loss_urgentFlagPersisted() {
        CargoIncident exception = CargoIncident.create(
                ExceptionId.generate(), "TRK-AB999999", ExceptionType.LOSS, "SGSIN",
                LocalDateTime.of(2026, 5, 31, 8, 0), "保管中に紛失"
        );
        cargoExceptionRepository.save(exception);

        List<CargoIncident> found = cargoExceptionRepository.findByTrackingNumber("TRK-AB999999");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).isUrgent()).isTrue();
    }

    @Test
    @DisplayName("同一追跡番号に複数の例外を保存できる")
    void saveMultipleExceptionsForSameTrackingNumber() {
        cargoExceptionRepository.save(CargoIncident.create(
                ExceptionId.generate(), "TRK-AB111111", ExceptionType.DELAY, "JPTYO",
                LocalDateTime.of(2026, 5, 10, 10, 0), "遅延理由"
        ));
        cargoExceptionRepository.save(CargoIncident.create(
                ExceptionId.generate(), "TRK-AB111111", ExceptionType.DAMAGE, "USNYC",
                LocalDateTime.of(2026, 5, 20, 14, 0), "破損理由"
        ));

        List<CargoIncident> found = cargoExceptionRepository.findByTrackingNumber("TRK-AB111111");
        assertThat(found).hasSize(2);
    }

    @Test
    @DisplayName("存在しない追跡番号では空リストを返す")
    void findByTrackingNumber_notFound_returnsEmpty() {
        List<CargoIncident> found = cargoExceptionRepository.findByTrackingNumber("TRK-NOT-FOUND");
        assertThat(found).isEmpty();
    }
}
