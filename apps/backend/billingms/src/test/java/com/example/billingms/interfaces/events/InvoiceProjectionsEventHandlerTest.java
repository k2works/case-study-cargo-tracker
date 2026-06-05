package com.example.billingms.interfaces.events;

import com.example.billingms.application.projections.InvoiceProjection;
import com.example.billingms.domain.events.DiscountAppliedEvent;
import com.example.billingms.domain.events.InvoiceCalculatedEvent;
import com.example.billingms.domain.events.InvoiceIssuedEvent;
import com.example.billingms.domain.events.InvoiceOverdueEvent;
import com.example.shared.events.PaymentRecordedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@link InvoiceProjectionsEventHandler} の Axon EventHandler テスト（IT7 review M1 リファクタ後）。
 *
 * <p>EventHandler は {@link InvoiceProjection} への薄いディスパッチ層であるため、各 event が
 * 対応する {@code apply(event)} に正しく委譲されることのみを検証する。Mapper 呼び出しの詳細は
 * {@link com.example.billingms.application.projections.InvoiceProjectionTest} で検証する。</p>
 */
class InvoiceProjectionsEventHandlerTest {

    private InvoiceProjection projection;
    private InvoiceProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        projection = mock(InvoiceProjection.class);
        handler = new InvoiceProjectionsEventHandler(projection);
    }

    @Test
    @DisplayName("US21: InvoiceCalculatedEvent → projection.apply 委譲")
    void calculated_dispatch() {
        InvoiceCalculatedEvent event = new InvoiceCalculatedEvent(
                "INV-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY", LocalDateTime.now());

        handler.on(event);

        verify(projection).apply(event);
    }

    @Test
    @DisplayName("US22: DiscountAppliedEvent → projection.apply 委譲")
    void discount_dispatch() {
        DiscountAppliedEvent event = new DiscountAppliedEvent(
                "INV-001", "S-001",
                new BigDecimal("0.15"), new BigDecimal("49500"),
                new BigDecimal("280500"), LocalDateTime.now());

        handler.on(event);

        verify(projection).apply(event);
    }

    @Test
    @DisplayName("US23: InvoiceIssuedEvent → projection.apply 委譲")
    void issued_dispatch() {
        InvoiceIssuedEvent event = new InvoiceIssuedEvent(
                "INV-001", "S-001", "INV-20260820-0001",
                LocalDate.of(2026, 9, 19),
                new BigDecimal("330000"), LocalDateTime.now());

        handler.on(event);

        verify(projection).apply(event);
    }

    @Test
    @DisplayName("US23: PaymentRecordedEvent → projection.apply 委譲")
    void payment_dispatch() {
        PaymentRecordedEvent event = new PaymentRecordedEvent(
                "INV-001", "PAY-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY",
                LocalDateTime.now(), LocalDateTime.now());

        handler.on(event);

        verify(projection).apply(event);
    }

    @Test
    @DisplayName("US23: InvoiceOverdueEvent → projection.apply 委譲")
    void overdue_dispatch() {
        InvoiceOverdueEvent event = new InvoiceOverdueEvent(
                "INV-001", "S-001", LocalDateTime.now());

        handler.on(event);

        verify(projection).apply(event);
    }
}
