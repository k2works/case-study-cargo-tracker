package com.example.billingms.interfaces.events;

import com.example.billingms.domain.events.PaymentRecordedEvent;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * {@link SharedPaymentRecordedEventPublisher} のテスト（US23、IT7 T4.5）。
 *
 * <p>billingms 内部 PaymentRecordedEvent → shared/events PaymentRecordedEvent への変換が
 * 正しく行われ、EventGateway 経由で発行されることを検証する。</p>
 */
@ExtendWith(MockitoExtension.class)
class SharedPaymentRecordedEventPublisherTest {

    @Mock
    private EventGateway eventGateway;

    @InjectMocks
    private SharedPaymentRecordedEventPublisher publisher;

    @Test
    void 内部Eventをshared用に変換して発行する() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 9, 15, 14, 30);
        LocalDateTime recordedAt = LocalDateTime.of(2026, 9, 15, 14, 35);
        PaymentRecordedEvent internal = new PaymentRecordedEvent(
                "INV-001", "PAY-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY",
                paidAt, "BANK_TRANSFER", "TXN-001", recordedAt
        );

        publisher.on(internal);

        ArgumentCaptor<com.example.shared.events.PaymentRecordedEvent> captor =
                ArgumentCaptor.forClass(com.example.shared.events.PaymentRecordedEvent.class);
        verify(eventGateway).publish(captor.capture());
        com.example.shared.events.PaymentRecordedEvent shared = captor.getValue();
        assertThat(shared.invoiceId()).isEqualTo("INV-001");
        assertThat(shared.paymentId()).isEqualTo("PAY-001");
        assertThat(shared.bookingId()).isEqualTo("B-001");
        assertThat(shared.shipperId()).isEqualTo("S-001");
        assertThat(shared.paidAmount()).isEqualByComparingTo("330000");
        assertThat(shared.currency()).isEqualTo("JPY");
        assertThat(shared.paidAt()).isEqualTo(paidAt);
        assertThat(shared.recordedAt()).isEqualTo(recordedAt);
    }
}
