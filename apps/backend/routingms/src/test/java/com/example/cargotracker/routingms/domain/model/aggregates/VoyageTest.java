package com.example.cargotracker.routingms.domain.model.aggregates;

import com.example.cargotracker.routingms.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routingms.domain.model.commands.UpdateVoyageScheduleCommand;
import com.example.cargotracker.routingms.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routingms.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import com.example.cargotracker.routingms.domain.model.valueobjects.VoyageStatus;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Voyage Aggregate のユニットテスト（Mockito ベース）。
 *
 * <p>AxonTestFixture が API 不一致のため US04 と同じ Mockito ベースで実装（ADR-0007 注記）。</p>
 */
@DisplayName("Voyage Aggregate（ユニット）")
class VoyageTest {

    private static final UnLocode JPYOK = new UnLocode("JPYOK");
    private static final UnLocode USLAX = new UnLocode("USLAX");
    private static final UnLocode TWKHH = new UnLocode("TWKHH");
    private static final LocalDateTime DEPART = LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime ARRIVE = LocalDateTime.of(2026, 7, 15, 18, 0);

    private RegisterVoyageCommand validCommand() {
        var movements = List.of(
                new CarrierMovement(JPYOK, TWKHH, DEPART, DEPART.plusDays(4)),
                new CarrierMovement(TWKHH, USLAX, DEPART.plusDays(5), ARRIVE));
        return new RegisterVoyageCommand(
                "V12345",
                new Carrier("MOL", "Mitsui O.S.K. Lines"),
                "Yokohama Express",
                JPYOK, USLAX,
                DEPART, ARRIVE,
                movements,
                List.of(CargoType.GENERAL, CargoType.REFRIGERATED));
    }

    @Test
    @DisplayName("register は EventAppender に VoyageRegisteredEvent を追加する")
    void register_イベント追加() {
        EventAppender appender = mock(EventAppender.class);
        var command = validCommand();

        String returned = Voyage.register(command, appender);

        assertThat(returned).isEqualTo("V12345");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(VoyageRegisteredEvent.class);
        VoyageRegisteredEvent event = (VoyageRegisteredEvent) captor.getValue();
        assertThat(event.voyageNumber()).isEqualTo("V12345");
        assertThat(event.shipName()).isEqualTo("Yokohama Express");
        assertThat(event.carrierMovements()).hasSize(2);
        assertThat(event.acceptedCargoTypes()).containsExactly(CargoType.GENERAL, CargoType.REFRIGERATED);
    }

    @Test
    @DisplayName("VoyageRegisteredEvent を再生すると SCHEDULED で復元される")
    void on_イベント再生で状態復元() {
        var voyage = new Voyage();
        var command = validCommand();
        var event = new VoyageRegisteredEvent(
                command.voyageNumber(), command.carrier(), command.shipName(),
                command.origin(), command.destination(),
                command.departureDate(), command.arrivalDate(),
                command.carrierMovements(), command.acceptedCargoTypes());

        voyage.on(event);

        assertThat(voyage.getVoyageNumber()).isEqualTo("V12345");
        assertThat(voyage.getShipName()).isEqualTo("Yokohama Express");
        assertThat(voyage.getStatus()).isEqualTo(VoyageStatus.SCHEDULED);
        assertThat(voyage.getCarrierMovements()).hasSize(2);
        assertThat(voyage.getAcceptedCargoTypes()).containsExactly(CargoType.GENERAL, CargoType.REFRIGERATED);
    }

    @Test
    @DisplayName("Command の日付逆転（arrival <= departure）は拒否される")
    void 日付整合性違反は拒否() {
        var carrier = new Carrier("MOL", "MOL");
        var movements = List.of(new CarrierMovement(JPYOK, USLAX, DEPART, ARRIVE));
        var cargoTypes = List.of(CargoType.GENERAL);
        assertThatThrownBy(() -> new RegisterVoyageCommand(
                "V99", carrier, "Ship", JPYOK, USLAX, ARRIVE, DEPART, movements, cargoTypes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arrivalDate");
    }

    @Test
    @DisplayName("Command の origin == destination は拒否される")
    void 同一OriginDestinationは拒否() {
        var carrier = new Carrier("MOL", "MOL");
        var movements = List.of(new CarrierMovement(JPYOK, USLAX, DEPART, ARRIVE));
        var cargoTypes = List.of(CargoType.GENERAL);
        assertThatThrownBy(() -> new RegisterVoyageCommand(
                "V99", carrier, "Ship", JPYOK, JPYOK, DEPART, ARRIVE, movements, cargoTypes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Command の carrierMovements が空は拒否される")
    void 寄港地空は拒否() {
        var carrier = new Carrier("MOL", "MOL");
        var emptyMovements = List.<CarrierMovement>of();
        var cargoTypes = List.of(CargoType.GENERAL);
        assertThatThrownBy(() -> new RegisterVoyageCommand(
                "V99", carrier, "Ship", JPYOK, USLAX, DEPART, ARRIVE, emptyMovements, cargoTypes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== US25: 既存航海スケジュール更新 =====

    private UpdateVoyageScheduleCommand validUpdateCommand() {
        var newDepart = DEPART.plusDays(2);
        var newArrive = ARRIVE.plusDays(2);
        var movements = List.of(
                new CarrierMovement(JPYOK, TWKHH, newDepart, newDepart.plusDays(4)),
                new CarrierMovement(TWKHH, USLAX, newDepart.plusDays(5), newArrive));
        return new UpdateVoyageScheduleCommand(
                "V12345",
                newDepart,
                newArrive,
                movements,
                List.of(CargoType.GENERAL, CargoType.HAZARDOUS));
    }

    @Test
    @DisplayName("updateSchedule は EventAppender に VoyageScheduleUpdatedEvent を追加する")
    void updateSchedule_イベント追加() {
        EventAppender appender = mock(EventAppender.class);
        var voyage = registeredVoyage();
        var command = validUpdateCommand();

        voyage.updateSchedule(command, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(VoyageScheduleUpdatedEvent.class);
        VoyageScheduleUpdatedEvent event = (VoyageScheduleUpdatedEvent) captor.getValue();
        assertThat(event.voyageNumber()).isEqualTo("V12345");
        assertThat(event.carrierMovements()).hasSize(2);
        assertThat(event.acceptedCargoTypes()).containsExactly(CargoType.GENERAL, CargoType.HAZARDOUS);
    }

    @Test
    @DisplayName("VoyageScheduleUpdatedEvent を再生すると carrierMovements と日付が上書きされる")
    void on_スケジュール更新で状態反映() {
        var voyage = registeredVoyage();
        var update = validUpdateCommand();
        var event = new VoyageScheduleUpdatedEvent(
                update.voyageNumber(),
                update.departureDate(),
                update.arrivalDate(),
                update.carrierMovements(),
                update.acceptedCargoTypes());

        voyage.on(event);

        assertThat(voyage.getDepartureDate()).isEqualTo(update.departureDate());
        assertThat(voyage.getArrivalDate()).isEqualTo(update.arrivalDate());
        assertThat(voyage.getCarrierMovements()).hasSize(2);
        assertThat(voyage.getAcceptedCargoTypes()).containsExactly(CargoType.GENERAL, CargoType.HAZARDOUS);
        // 登録時の Carrier / shipName は更新対象外なので維持される
        assertThat(voyage.getShipName()).isEqualTo("Yokohama Express");
    }

    @Test
    @DisplayName("Command の日付逆転（arrival <= departure）は拒否される")
    void 更新Command_日付逆転は拒否() {
        var movements = List.of(new CarrierMovement(JPYOK, USLAX, DEPART, ARRIVE));
        var cargoTypes = List.of(CargoType.GENERAL);
        assertThatThrownBy(() -> new UpdateVoyageScheduleCommand(
                "V12345", ARRIVE, DEPART, movements, cargoTypes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arrivalDate");
    }

    @Test
    @DisplayName("Command の carrierMovements が空は拒否される")
    void 更新Command_寄港地空は拒否() {
        var emptyMovements = List.<CarrierMovement>of();
        var cargoTypes = List.of(CargoType.GENERAL);
        assertThatThrownBy(() -> new UpdateVoyageScheduleCommand(
                "V12345", DEPART, ARRIVE, emptyMovements, cargoTypes))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Command の carrierMovements の連続性違反（前の到着港 != 次の出発港）は拒否される")
    void 更新Command_連続性違反は拒否() {
        // 港湾連続性: [JPYOK→TWKHH], [USLAX→USNYC] は TWKHH != USLAX なので不正
        var disconnected = List.of(
                new CarrierMovement(JPYOK, TWKHH, DEPART, DEPART.plusDays(4)),
                new CarrierMovement(USLAX, new UnLocode("USNYC"), DEPART.plusDays(5), ARRIVE));
        var cargoTypes = List.of(CargoType.GENERAL);
        assertThatThrownBy(() -> new UpdateVoyageScheduleCommand(
                "V12345", DEPART, ARRIVE, disconnected, cargoTypes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("連続性");
    }

    /**
     * テスト用ヘルパ: 登録済みの Voyage を Event 再生で構築する。
     */
    private Voyage registeredVoyage() {
        var voyage = new Voyage();
        var register = validCommand();
        voyage.on(new VoyageRegisteredEvent(
                register.voyageNumber(),
                register.carrier(),
                register.shipName(),
                register.origin(),
                register.destination(),
                register.departureDate(),
                register.arrivalDate(),
                register.carrierMovements(),
                register.acceptedCargoTypes()));
        return voyage;
    }
}
