package com.example.routingms.interfaces.events;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import com.example.shared.events.RouteDesignRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteDesignRequestEventHandlerTest {

    @Mock
    private RouteDesignRequestMapper mapper;

    @InjectMocks
    private RouteDesignRequestEventHandler handler;

    private RouteDesignRequestedEvent event(String bookingId) {
        return new RouteDesignRequestedEvent(bookingId, "ROUTING", "JPTYO", "USNYC",
                LocalDate.of(2026, 9, 30), "GENERAL");
    }

    @Test
    @DisplayName("US06: 未登録の経路設計依頼を受信すると read model に記録される")
    void 未登録の経路設計依頼を記録する() {
        when(mapper.findByBookingId("B-001")).thenReturn(null);

        handler.on(event("B-001"));

        verify(mapper).insert("B-001", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL");
    }

    @Test
    @DisplayName("ADR-0009: 既登録の経路設計依頼は再記録しない（tracking 再処理の冪等性）")
    void 既登録の経路設計依頼は再記録しない() {
        when(mapper.findByBookingId("B-001")).thenReturn(new RouteDesignRequestProjection());

        handler.on(event("B-001"));

        verify(mapper, never()).insert(anyString(), anyString(), anyString(),
                any(LocalDate.class), anyString());
    }
}
