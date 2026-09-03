package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.LocalDate;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 復元した集約が判断に効くことを、実 Axon Server（DCB 有効）で確かめる。
 *
 * <p>{@code AxonTestFixture} の {@code disableAxonServer()} ではタグによる復元が
 * 働かず、事前活動を積んでも集約は空のままになる。つまり「2 度目を断る」守りを
 * 外しても緑になる。判別できる場所はここしかない（IT2 で実測）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CargoBookingIT extends AbstractAxonIntegrationTest {

    @Autowired
    private CommandGateway commandGateway;

    private static BookCargoCommand book(String bookingId) {
        return new BookCargoCommand(bookingId, "SHP-000001",
                new CargoSpecification(CargoType.GENERAL, Weight.ofKilograms("1200"),
                        Dimensions.of("120", "80", "100"), 10, "自動車部品", null, null),
                new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2026, 12, 1)),
                "sales01");
    }

    @Test
    @DisplayName("同じ予約 ID で 2 度受け付けない")
    void rejectsDoubleBooking() {
        String bookingId = "B-IT-" + System.nanoTime();

        assertThat(commandGateway.sendAndWait(book(bookingId), String.class)).isEqualTo(bookingId);

        // 通すとイベント列に予約が 2 本並び、どちらが正か決まらない。
        // 型ではなく文言で見る。サービス越しに来た例外は
        // AxonServerRemoteCommandHandlingException に包まれ、根の型が置き換わる。
        assertThatThrownBy(() -> commandGateway.sendAndWait(book(bookingId), String.class))
                .hasMessageContaining("既に受け付けています");
    }
}
