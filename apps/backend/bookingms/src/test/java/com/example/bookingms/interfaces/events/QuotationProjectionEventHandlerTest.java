package com.example.bookingms.interfaces.events;

import com.example.bookingms.domain.events.QuotationCreatedEvent;
import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.Dimensions;
import com.example.bookingms.domain.model.RouteCandidate;
import com.example.bookingms.domain.model.RouteSpecification;
import com.example.bookingms.infrastructure.repositories.mybatis.QuotationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuotationProjectionEventHandlerTest {

    @Mock
    private QuotationMapper quotationMapper;

    @InjectMocks
    private QuotationProjectionEventHandler handler;

    private CargoSpecification generalSpec() {
        return new CargoSpecification(
                CargoType.GENERAL,
                new BigDecimal("1500.00"),
                new Dimensions(120, 80, 60),
                10,
                "電子部品");
    }

    @Test
    @DisplayName("US01: 見積イベント受信で quotation と candidate を INSERT する")
    void 見積イベント受信でquotationとcandidateがinsertされる() {
        QuotationCreatedEvent event = new QuotationCreatedEvent(
                "Q-001", "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                generalSpec(),
                List.of(new RouteCandidate("JPTYO → USNYC（直行）", 14, new BigDecimal("850000"), "JPY")),
                new BigDecimal("850000"), "JPY", LocalDate.of(2026, 8, 31), "DRAFT");

        handler.on(event);

        verify(quotationMapper).insertQuotation(
                "Q-001", "S-001", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30),
                "GENERAL", new BigDecimal("1500.00"), new BigDecimal("850000"),
                "JPY", LocalDate.of(2026, 8, 31), "DRAFT");
        verify(quotationMapper).insertCandidate(
                "Q-001", 1, 14, new BigDecimal("850000"), "JPY", "JPTYO → USNYC（直行）");
    }

    @Test
    @DisplayName("US01: 候補なしイベント受信で quotation のみ INSERT し candidate は INSERT しない")
    void 候補なしイベント受信でquotationのみinsertされる() {
        QuotationCreatedEvent event = new QuotationCreatedEvent(
                "Q-002", "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                generalSpec(),
                List.of(), null, null, LocalDate.of(2026, 8, 31), "DRAFT");

        handler.on(event);

        verify(quotationMapper).insertQuotation(
                "Q-002", "S-001", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30),
                "GENERAL", new BigDecimal("1500.00"), null, null,
                LocalDate.of(2026, 8, 31), "DRAFT");
        verify(quotationMapper, never()).insertCandidate(any(), anyInt(), anyInt(), any(), any(), any());
    }
}
