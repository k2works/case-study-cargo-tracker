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

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipperProjectionEventHandlerTest {

    @Mock
    private ShipperMapper shipperMapper;

    @InjectMocks
    private ShipperProjectionEventHandler handler;

    @Test
    @DisplayName("US02: 個人荷主の ShipperRegisteredEvent で contractNumber / discountRate は null で INSERT")
    void 個人荷主は契約情報なしでINSERT() {
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
                "03-1234-5678",
                null,
                null);

        handler.on(event);

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
                "03-1234-5678",
                null,
                null);
    }

    @Test
    @DisplayName("US03: 法人荷主の ShipperRegisteredEvent で contractNumber / discountRate が INSERT される")
    void 法人荷主は契約情報付きでINSERT() {
        ShipperRegisteredEvent event = new ShipperRegisteredEvent(
                "S-100",
                ShipperType.CORPORATE,
                "株式会社グローバル商事",
                "東京都港区六本木 6-10-1",
                "ミッドタウンタワー 30F",
                "港区",
                "JP",
                "106-6130",
                "biz@global.example.com",
                "03-5555-0001",
                "CONTRACT-2026-001",
                new BigDecimal("0.150"));

        handler.on(event);

        verify(shipperMapper).insertShipper(
                "S-100",
                "CORPORATE",
                "株式会社グローバル商事",
                "東京都港区六本木 6-10-1",
                "ミッドタウンタワー 30F",
                "港区",
                "JP",
                "106-6130",
                "biz@global.example.com",
                "03-5555-0001",
                "CONTRACT-2026-001",
                new BigDecimal("0.150"));
    }
}
