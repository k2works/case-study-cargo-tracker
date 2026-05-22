package com.example.routingms.domain.model;

import com.example.routingms.domain.commands.RegisterVoyageCommand;
import com.example.routingms.domain.commands.UpdateVoyageScheduleCommand;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import com.example.routingms.domain.events.VoyageScheduleUpdatedEvent;
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
                        command.movements(),
                        List.of("GENERAL")
                ));
    }

    @Test
    void 既存航海スケジュールを更新できる() {
        // Given: 登録済みの航海
        var dep1 = LocalDateTime.of(2026, 6, 1, 10, 0);
        var arr1 = LocalDateTime.of(2026, 6, 10, 18, 0);

        // When: スケジュール更新コマンド
        var dep2 = LocalDateTime.of(2026, 7, 1, 10, 0);
        var arr2 = LocalDateTime.of(2026, 7, 15, 18, 0);
        var updateCmd = new UpdateVoyageScheduleCommand(
                "V001", dep2, arr2,
                List.of(new RegisterVoyageCommand.CarrierMovementData("JPTYO", "USNYC", dep2, arr2)),
                List.of("GENERAL", "REFRIGERATED"));

        // Then: VoyageScheduleUpdatedEvent が発行される
        fixture.given(new VoyageRegisteredEvent(
                        "V001", "CARRIER-A", "運送会社A", "SHIPα",
                        "JPTYO", "USNYC", dep1, arr1, List.of(), List.of("GENERAL")))
                .when(updateCmd)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new VoyageScheduleUpdatedEvent(
                        "V001", dep2, arr2, updateCmd.movements(), List.of("GENERAL", "REFRIGERATED")));
    }

    @Test
    void 更新時に到着日が出発日以前の場合は例外が発生する() {
        // Given: 登録済みの航海
        var dep1 = LocalDateTime.of(2026, 6, 1, 10, 0);
        var arr1 = LocalDateTime.of(2026, 6, 10, 18, 0);

        // When: 日付不整合の更新コマンド
        var dep2 = LocalDateTime.of(2026, 7, 15, 10, 0);
        var arr2 = LocalDateTime.of(2026, 7, 1, 18, 0); // 出発より前
        var updateCmd = new UpdateVoyageScheduleCommand(
                "V001", dep2, arr2, List.of(), List.of());

        // Then: IllegalArgumentException が発生する
        fixture.given(new VoyageRegisteredEvent(
                        "V001", "CARRIER-A", "運送会社A", "SHIPα",
                        "JPTYO", "USNYC", dep1, arr1, List.of(), List.of("GENERAL")))
                .when(updateCmd)
                .expectException(IllegalArgumentException.class);
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
