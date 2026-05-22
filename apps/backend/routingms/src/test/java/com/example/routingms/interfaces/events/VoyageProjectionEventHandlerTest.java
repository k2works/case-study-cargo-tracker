package com.example.routingms.interfaces.events;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import com.example.routingms.domain.events.VoyageRegisteredEvent;
import com.example.routingms.domain.events.VoyageScheduleUpdatedEvent;
import com.example.routingms.infrastructure.repositories.mybatis.VoyageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VoyageProjectionEventHandlerTest {

    @Mock
    private VoyageMapper voyageMapper;

    @InjectMocks
    private VoyageProjectionEventHandler handler;

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 6, 10, 18, 0);

    @Test
    void 航海登録イベントでDBに挿入される() {
        var movement = new CarrierMovementData("JPTYO", "USNYC", DEPARTURE, ARRIVAL);
        var event = new VoyageRegisteredEvent(
                "V001", "CARRIER-A", "運送会社A", "SHIPα",
                "JPTYO", "USNYC", DEPARTURE, ARRIVAL,
                List.of(movement), List.of("GENERAL"));

        handler.on(event);

        verify(voyageMapper).insertVoyage("V001", "CARRIER-A", "運送会社A", "SHIPα",
                "JPTYO", "USNYC", DEPARTURE, ARRIVAL);
        verify(voyageMapper).insertCarrierMovement("V001", 0, "JPTYO", "USNYC", DEPARTURE, ARRIVAL);
        verify(voyageMapper).insertAcceptedCargoType("V001", "GENERAL");
    }

    @Test
    void スケジュール更新イベントでDBが更新される() {
        var movement = new CarrierMovementData("JPTYO", "USNYC", DEPARTURE, ARRIVAL);
        var event = new VoyageScheduleUpdatedEvent(
                "V001", DEPARTURE, ARRIVAL, List.of(movement), List.of("REFRIGERATED"));

        handler.on(event);

        verify(voyageMapper).updateVoyage("V001", DEPARTURE, ARRIVAL);
        verify(voyageMapper).deleteCarrierMovements("V001");
        verify(voyageMapper).insertCarrierMovement("V001", 0, "JPTYO", "USNYC", DEPARTURE, ARRIVAL);
        verify(voyageMapper).deleteAcceptedCargoTypes("V001");
        verify(voyageMapper).insertAcceptedCargoType("V001", "REFRIGERATED");
    }
}
