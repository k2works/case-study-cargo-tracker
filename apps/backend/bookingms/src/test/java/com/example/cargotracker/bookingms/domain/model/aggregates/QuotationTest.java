package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.commands.CreateQuotationCommand;
import com.example.cargotracker.bookingms.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Money;
import com.example.cargotracker.bookingms.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Quotation Aggregate のユニットテスト（US01 / UC01）。
 *
 * <p>Cargo Aggregate と同じ Mockito ベースで EventAppender 経由のイベント発行を検証する。</p>
 */
@DisplayName("Quotation Aggregate（ユニット）")
class QuotationTest {

    private static final ShipperId SHIPPER = new ShipperId(1L);

    private CreateQuotationCommand commandWithDeadline(LocalDate deadline,
                                                       CargoType cargoType,
                                                       HazardInfo hazardInfo) {
        var route = new RouteSpecification(
                Location.of("JPTYO"),
                Location.of("USNYC"),
                deadline);
        return new CreateQuotationCommand(
                UUID.randomUUID().toString(),
                SHIPPER,
                route,
                cargoType,
                new BigDecimal("100"),
                hazardInfo);
    }

    @Test
    @DisplayName("一般貨物の見積作成で OFFERED 状態のイベントが発行される")
    void 一般貨物_OFFERED() {
        EventAppender appender = mock(EventAppender.class);
        var command = commandWithDeadline(LocalDate.now().plusDays(30), CargoType.GENERAL, null);

        String returned = Quotation.create(command, appender);

        assertThat(returned).isEqualTo(command.quotationId());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(QuotationCreatedEvent.class);

        QuotationCreatedEvent event = (QuotationCreatedEvent) captor.getValue();
        assertThat(event.status()).isEqualTo(QuotationStatus.OFFERED);
        assertThat(event.candidates()).hasSize(1);
        assertThat(event.candidates().get(0).itinerarySummary()).isEqualTo("JPTYO → USNYC");
        assertThat(event.candidates().get(0).estimatedDays()).isEqualTo(14);
        assertThat(event.estimatedAmount()).isEqualTo(new Money(new BigDecimal("100000.00"), "JPY"));
        assertThat(event.validUntil()).isEqualTo(LocalDate.now().plusDays(7));
    }

    @Test
    @DisplayName("受入条件 5: 期限内に到達できないルートのみなら候補ゼロで DRAFT を返す")
    void 期限内ルートなし_DRAFT() {
        EventAppender appender = mock(EventAppender.class);
        // deadline までの日数が 14 日未満（直行ルートの所要日数を満たさない）
        var command = commandWithDeadline(LocalDate.now().plusDays(7), CargoType.GENERAL, null);

        Quotation.create(command, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        QuotationCreatedEvent event = (QuotationCreatedEvent) captor.getValue();
        assertThat(event.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(event.candidates()).isEmpty();
    }

    @Test
    @DisplayName("HAZARDOUS は 1.3 倍係数で料金が増額される")
    void HAZARDOUS_料金加算() {
        EventAppender appender = mock(EventAppender.class);
        var hazard = new HazardInfo("Class 3", "UN1170", "ethanol");
        var command = commandWithDeadline(LocalDate.now().plusDays(30), CargoType.HAZARDOUS, hazard);

        Quotation.create(command, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        QuotationCreatedEvent event = (QuotationCreatedEvent) captor.getValue();
        // 100 kg * 1000 JPY * 1.3 = 130,000 JPY
        assertThat(event.estimatedAmount()).isEqualTo(new Money(new BigDecimal("130000.00"), "JPY"));
    }

    @Test
    @DisplayName("受入条件 6: HAZARDOUS で hazardInfo が無いと CreateQuotationCommand が拒否される")
    void HAZARDOUS_申告無しは拒否() {
        String id = UUID.randomUUID().toString();
        var route = new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"), LocalDate.now().plusDays(30));
        var weight = new BigDecimal("100");
        assertThatThrownBy(() -> new CreateQuotationCommand(id, SHIPPER, route, CargoType.HAZARDOUS, weight, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hazardInfo");
    }

    @Test
    @DisplayName("Command の weightKg <= 0 は拒否される")
    void 重量0以下は拒否() {
        String id = UUID.randomUUID().toString();
        var route = new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"), LocalDate.now().plusDays(30));
        assertThatThrownBy(() -> new CreateQuotationCommand(id, SHIPPER, route, CargoType.GENERAL, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weightKg");
    }

    @Test
    @DisplayName("QuotationCreatedEvent を再生すると状態が復元される")
    void on_イベント再生で状態復元() {
        var quotation = new Quotation();
        var route = new RouteSpecification(
                Location.of("JPTYO"), Location.of("USNYC"), LocalDate.now().plusDays(30));
        var event = new QuotationCreatedEvent(
                "Q-RESTORE-1",
                SHIPPER,
                route,
                CargoType.GENERAL,
                new BigDecimal("100"),
                null,
                new Money(new BigDecimal("100000.00"), "JPY"),
                java.util.List.of(),
                LocalDate.now().plusDays(7),
                QuotationStatus.OFFERED);

        quotation.on(event);

        assertThat(quotation.getQuotationId()).isEqualTo("Q-RESTORE-1");
        assertThat(quotation.getShipperId()).isEqualTo(SHIPPER);
        assertThat(quotation.getCargoType()).isEqualTo(CargoType.GENERAL);
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.OFFERED);
    }
}
