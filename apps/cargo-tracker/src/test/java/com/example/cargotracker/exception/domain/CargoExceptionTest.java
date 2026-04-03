package com.example.cargotracker.exception.domain;

import com.example.cargotracker.exception.domain.model.aggregates.CargoIncident;
import com.example.cargotracker.exception.domain.model.aggregates.ExceptionId;
import com.example.cargotracker.exception.domain.model.valueobjects.ExceptionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CargoException 集約")
class CargoExceptionTest {

    // ── 正常系 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("遅延例外を記録できる")
    void record_delay_createsCargoException() {
        CargoIncident exception = CargoIncident.create(
                ExceptionId.generate(),
                "TRK-AB123456",
                ExceptionType.DELAY,
                "JPTYO",
                LocalDateTime.of(2026, 5, 28, 10, 0),
                "悪天候による港湾閉鎖"
        );

        assertThat(exception.getId()).isNotNull();
        assertThat(exception.getTrackingNumber()).isEqualTo("TRK-AB123456");
        assertThat(exception.getExceptionType()).isEqualTo(ExceptionType.DELAY);
        assertThat(exception.getLocationCode()).isEqualTo("JPTYO");
        assertThat(exception.getReason()).isEqualTo("悪天候による港湾閉鎖");
        assertThat(exception.isUrgent()).isFalse();
        assertThat(exception.getResolution()).isNull();
    }

    @Test
    @DisplayName("破損例外を記録できる")
    void record_damage_createsCargoException() {
        CargoIncident exception = CargoIncident.create(
                ExceptionId.generate(),
                "TRK-AB123456",
                ExceptionType.DAMAGE,
                "USNYC",
                LocalDateTime.of(2026, 5, 30, 14, 0),
                "積み降ろし中に破損"
        );

        assertThat(exception.getExceptionType()).isEqualTo(ExceptionType.DAMAGE);
        assertThat(exception.isUrgent()).isFalse();
    }

    @Test
    @DisplayName("紛失例外は urgent フラグが自動的に true になる")
    void record_loss_setsUrgentFlag() {
        CargoIncident exception = CargoIncident.create(
                ExceptionId.generate(),
                "TRK-AB123456",
                ExceptionType.LOSS,
                "SGSIN",
                LocalDateTime.of(2026, 5, 31, 8, 0),
                "保管中に紛失"
        );

        assertThat(exception.getExceptionType()).isEqualTo(ExceptionType.LOSS);
        assertThat(exception.isUrgent()).isTrue();
    }

    @Test
    @DisplayName("例外に対応内容を記録できる")
    void resolve_updatesResolution() {
        CargoIncident exception = CargoIncident.create(
                ExceptionId.generate(),
                "TRK-AB123456",
                ExceptionType.DELAY,
                "JPTYO",
                LocalDateTime.of(2026, 5, 28, 10, 0),
                "悪天候"
        );

        exception.resolve("代替ルートで輸送。到着予定日を 2026-06-05 に変更");

        assertThat(exception.getResolution()).isEqualTo("代替ルートで輸送。到着予定日を 2026-06-05 に変更");
    }

    // ── 異常系 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("id が null の場合は IllegalArgumentException をスローする")
    void record_nullId_throwsException() {
        ExceptionId nullId = null;
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> CargoIncident.create(nullId, "TRK-AB123456", ExceptionType.DELAY, "JPTYO", now, "理由"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("追跡番号が null の場合は IllegalArgumentException をスローする")
    void record_nullTrackingNumber_throwsException() {
        ExceptionId id = ExceptionId.generate();
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> CargoIncident.create(id, null, ExceptionType.DELAY, "JPTYO", now, "理由"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("追跡番号が空文字の場合は IllegalArgumentException をスローする")
    void record_blankTrackingNumber_throwsException() {
        ExceptionId id = ExceptionId.generate();
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> CargoIncident.create(id, "  ", ExceptionType.DELAY, "JPTYO", now, "理由"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("例外種別が null の場合は IllegalArgumentException をスローする")
    void record_nullExceptionType_throwsException() {
        ExceptionId id = ExceptionId.generate();
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> CargoIncident.create(id, "TRK-AB123456", null, "JPTYO", now, "理由"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("発生日時が null の場合は IllegalArgumentException をスローする")
    void record_nullOccurredAt_throwsException() {
        ExceptionId id = ExceptionId.generate();
        LocalDateTime nullDateTime = null;
        assertThatThrownBy(() -> CargoIncident.create(id, "TRK-AB123456", ExceptionType.DELAY, "JPTYO", nullDateTime, "理由"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ExceptionId の generate が null でない UUID を返す")
    void exceptionId_generate_returnsNonNull() {
        ExceptionId id = ExceptionId.generate();
        assertThat(id).isNotNull();
        assertThat(id.value()).isNotNull();
    }

    @Test
    @DisplayName("ExceptionId の value が null の場合は IllegalArgumentException をスローする")
    void exceptionId_nullValue_throwsException() {
        assertThatThrownBy(() -> new ExceptionId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
