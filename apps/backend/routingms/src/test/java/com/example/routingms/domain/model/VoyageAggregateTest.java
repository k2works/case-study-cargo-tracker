package com.example.routingms.domain.model;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoyageAggregateTest {

    private FixtureConfiguration<Voyage> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Voyage.class);
    }

    @Test
    void 航海スケジュールを新規登録できる() {
        // Given: 何もない状態
        // When: 新規登録コマンド
        var departure = LocalDateTime.of(2026, 6, 1, 10, 0);
        var arrival = LocalDateTime.of(2026, 6, 10, 18, 0);
        var command = new RegisterVoyageCommand(
                "V001",
                "CARRIER-A",
                "運送会社A",
                "SHIPα",
                "JPTYO",
                "USNYC",
                departure,
                arrival,
                List.of(
                        new RegisterVoyageCommand.CarrierMovementData("JPTYO", "CNSHA", departure, departure.plusDays(3)),
                        new RegisterVoyageCommand.CarrierMovementData("CNSHA", "USNYC", departure.plusDays(3), arrival)
                ),
                List.of("GENERAL")
        );

        // Then: VoyageRegisteredEvent が発行される
        fixture.givenNoPriorActivity()
                .when(command)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new VoyageRegisteredEvent(
                        "V001",
                        "CARRIER-A",
                        "運送会社A",
                        "SHIPα",
                        "JPTYO",
                        "USNYC",
                        departure,
                        arrival,
                        command.getMovements(),
                        List.of("GENERAL")
                ));
    }

    @Test
    void 到着日が出発日より前の場合は例外が発生する() {
        // Given: 何もない状態
        // When: 日付不整合のコマンド
        var departure = LocalDateTime.of(2026, 6, 10, 10, 0);
        var arrival = LocalDateTime.of(2026, 6, 1, 18, 0); // 出発より前
        var command = new RegisterVoyageCommand(
                "V001",
                "CARRIER-A",
                "運送会社A",
                "SHIPα",
                "JPTYO",
                "USNYC",
                departure,
                arrival,
                List.of(),
                List.of()
        );

        // Then: IllegalArgumentException が発生する
        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(IllegalArgumentException.class);
    }
}
