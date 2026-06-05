package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.commands.MarkBookingSettledCommand;
import com.example.shared.events.PaymentRecordedEvent;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrossBillingPaymentHandler} cross-service ハンドラのユニットテスト（US23、IT7 T4.5）。
 */
@ExtendWith(MockitoExtension.class)
class CrossBillingPaymentHandlerTest {

    @Mock
    private CommandGateway commandGateway;

    @InjectMocks
    private CrossBillingPaymentHandler handler;

    private PaymentRecordedEvent event() {
        return new PaymentRecordedEvent(
                "INV-001", "PAY-001", "B-001", "S-001",
                new BigDecimal("330000"), "JPY",
                LocalDateTime.of(2026, 9, 15, 14, 30),
                LocalDateTime.of(2026, 9, 15, 14, 35)
        );
    }

    @Test
    void PaymentRecordedEventを受信するとMarkBookingSettledCommandを発行する() {
        handler.on(event());

        ArgumentCaptor<MarkBookingSettledCommand> captor =
                ArgumentCaptor.forClass(MarkBookingSettledCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().bookingId()).isEqualTo("B-001");
    }

    @Test
    void 対象予約が存在しない場合は冪等にスキップして伝播しない() {
        when(commandGateway.sendAndWait(any(MarkBookingSettledCommand.class)))
                .thenThrow(new AggregateNotFoundException("B-001", "not found"));

        assertThatCode(() -> handler.on(event())).doesNotThrowAnyException();
    }

    @Test
    void 集約遷移失敗で冪等にスキップして伝播しない() {
        when(commandGateway.sendAndWait(any(MarkBookingSettledCommand.class)))
                .thenThrow(new CommandExecutionException("予約を精算済にできるのは...", null));

        assertThatCode(() -> handler.on(event())).doesNotThrowAnyException();
    }
}
