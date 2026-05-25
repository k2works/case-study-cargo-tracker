package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.ShipperRegisteredEvent;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.infrastructure.repositories.mybatis.ShipperMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipperProjectionEventHandlerTest {

    @Mock
    private ShipperMapper shipperMapper;

    @InjectMocks
    private ShipperProjectionEventHandler handler;

    @Test
    @DisplayName("US02: ShipperRegisteredEvent を受信すると shipper テーブルに INSERT する")
    void ShipperRegisteredEvent受信でinsertShipperが呼ばれる() {
        // Given
        ShipperRegisteredEvent event = new ShipperRegisteredEvent(
                "S-001",
                ShipperType.INDIVIDUAL,
                "山田太郎",
                "東京都千代田区丸の内 1-1",
                null,
                "千代田区",
                "JP",
                "100-0005",
                "yamada@example.com",
                "03-1234-5678");

        // When
        handler.on(event);

        // Then
        verify(shipperMapper).insertShipper(
                "S-001",
                "INDIVIDUAL",
                "山田太郎",
                "東京都千代田区丸の内 1-1",
                null,
                "千代田区",
                "JP",
                "100-0005",
                "yamada@example.com",
                "03-1234-5678");
    }
}
