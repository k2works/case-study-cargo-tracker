package com.example.routingms.application;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.commands.UpdateVoyageScheduleCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoyageCommandServiceTest {

    @Mock
    private CommandGateway commandGateway;

    @InjectMocks
    private VoyageCommandService service;

    @Test
    void 航海登録コマンドをゲートウェイに送信できる() {
        var departure = LocalDateTime.of(2026, 6, 1, 10, 0);
        var arrival = LocalDateTime.of(2026, 6, 10, 18, 0);
        var command = new RegisterVoyageCommand("V001", "C1", "社名", "船名",
                "JPTYO", "USNYC", departure, arrival, List.of(), List.of("GENERAL"));
        when(commandGateway.<String>send(any())).thenReturn(CompletableFuture.completedFuture("V001"));

        CompletableFuture<String> result = service.register(command);

        assertThat(result.join()).isEqualTo("V001");
        verify(commandGateway).send(command);
    }

    @Test
    void 航海更新コマンドをゲートウェイに送信できる() {
        var dep = LocalDateTime.of(2026, 7, 1, 10, 0);
        var arr = LocalDateTime.of(2026, 7, 15, 18, 0);
        var command = new UpdateVoyageScheduleCommand("V001", dep, arr, List.of(), List.of());
        when(commandGateway.<Void>send(any())).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Void> result = service.update(command);

        assertThat(result.join()).isNull();
        verify(commandGateway).send(command);
    }
}
