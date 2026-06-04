package com.example.billingms.interfaces.events;

import com.example.billingms.domain.commands.CalculateInvoiceCommand;
import com.example.billingms.infrastructure.outboundservices.BillingContextAcl;
import com.example.billingms.infrastructure.outboundservices.BillingContextInfo;
import com.example.shared.events.CargoDeliveredEvent;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CrossCargoDeliveredEventHandler} のユニットテスト（US21 / IT7 タスク 2.4）。
 *
 * <p>Mockito で {@link BillingContextAcl} と {@link CommandGateway} をモック化し、
 * {@code CargoDeliveredEvent} 受信時の動作を検証する。</p>
 */
class CrossCargoDeliveredEventHandlerTest {

    private CommandGateway commandGateway;
    private BillingContextAcl billingContextAcl;
    private CrossCargoDeliveredEventHandler handler;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        billingContextAcl = mock(BillingContextAcl.class);
        handler = new CrossCargoDeliveredEventHandler(commandGateway, billingContextAcl);
    }

    private BillingContextInfo defaultInfo() {
        return new BillingContextInfo(
                "S-001",
                new BigDecimal("1200"),
                "GENERAL",
                new BigDecimal("5300"),
                "JPTYO",
                "USNYC",
                8,
                "JPY"
        );
    }

    @Test
    @DisplayName("US21: CargoDeliveredEvent 受信で CalculateInvoiceCommand が発行される")
    void CargoDeliveredEvent受信でCommand発行() {
        when(billingContextAcl.loadFor("B-001", "TRK-AB12CD3456")).thenReturn(defaultInfo());

        handler.on(new CargoDeliveredEvent(
                "TRK-AB12CD3456",
                "B-001",
                LocalDateTime.of(2026, 8, 16, 14, 0)
        ));

        ArgumentCaptor<CalculateInvoiceCommand> captor = ArgumentCaptor.forClass(CalculateInvoiceCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        CalculateInvoiceCommand cmd = captor.getValue();

        assertThat(cmd.bookingId()).isEqualTo("B-001");
        assertThat(cmd.shipperId()).isEqualTo("S-001");
        assertThat(cmd.invoiceId()).isNotBlank();
        assertThat(cmd.transport().distanceKm()).isEqualByComparingTo("5300");
        assertThat(cmd.transport().weightKg()).isEqualByComparingTo("1200");
        assertThat(cmd.transport().cargoType()).isEqualTo("GENERAL");
        assertThat(cmd.transport().handlingCount()).isEqualTo(8);
        assertThat(cmd.transport().currency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("US21: BillingContextAcl の bookingId / trackingNumber を渡している")
    void ACLに正しいキーを渡す() {
        when(billingContextAcl.loadFor("B-XYZ", "TRK-FOO1234567")).thenReturn(defaultInfo());

        handler.on(new CargoDeliveredEvent(
                "TRK-FOO1234567", "B-XYZ", LocalDateTime.now()));

        verify(billingContextAcl).loadFor("B-XYZ", "TRK-FOO1234567");
    }

    @Test
    @DisplayName("ADR-0011: CommandExecutionException（bookingId 重複等）は WARN スキップで例外を伝播しない")
    void Command失敗時は冪等スキップ() {
        when(billingContextAcl.loadFor("B-DUP", "TRK-DUP123ABCD")).thenReturn(defaultInfo());
        when(commandGateway.sendAndWait(any(CalculateInvoiceCommand.class)))
                .thenThrow(new CommandExecutionException(
                        "Aggregate Identifier already exists", null));

        // 例外が外に伝播しないことを検証（IntelliJ では assertThatCode で書くが、ここでは
        // 単純に呼び出して例外が出なければ OK とする）
        handler.on(new CargoDeliveredEvent(
                "TRK-DUP123ABCD", "B-DUP", LocalDateTime.now()));

        verify(commandGateway).sendAndWait(any(CalculateInvoiceCommand.class));
    }

    @Test
    @DisplayName("US21: HAZARDOUS 貨物の TransportRecord が組み立てられる")
    void HAZARDOUS貨物の組立() {
        BillingContextInfo hazardous = new BillingContextInfo(
                "S-002", new BigDecimal("1000"), "HAZARDOUS",
                new BigDecimal("4000"), "JPTYO", "SGSIN", 5, "JPY");
        when(billingContextAcl.loadFor("B-002", "TRK-HAZ0123456")).thenReturn(hazardous);

        handler.on(new CargoDeliveredEvent(
                "TRK-HAZ0123456", "B-002", LocalDateTime.now()));

        ArgumentCaptor<CalculateInvoiceCommand> captor = ArgumentCaptor.forClass(CalculateInvoiceCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertThat(captor.getValue().transport().cargoType()).isEqualTo("HAZARDOUS");
    }
}
