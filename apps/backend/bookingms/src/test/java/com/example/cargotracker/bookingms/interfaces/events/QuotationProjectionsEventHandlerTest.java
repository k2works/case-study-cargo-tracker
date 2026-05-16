package com.example.cargotracker.bookingms.interfaces.events;

import com.example.cargotracker.bookingms.domain.model.events.QuotationCreatedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.bookingms.domain.model.valueobjects.HazardInfo;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Money;
import com.example.cargotracker.bookingms.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationCandidateRecord;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationMapper;
import com.example.cargotracker.bookingms.infrastructure.persistence.QuotationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class QuotationProjectionsEventHandlerTest {

    private QuotationMapper mapper;
    private QuotationProjectionsEventHandler handler;

    @BeforeEach
    void setUp() {
        mapper = mock(QuotationMapper.class);
        handler = new QuotationProjectionsEventHandler(mapper);
    }

    @Test
    @DisplayName("GENERAL 見積イベントで quotation と candidate が投影される")
    void 一般貨物見積が投影される() {
        var event = generalEvent(List.of(
                new RouteCandidate(14, new Money(new BigDecimal("100000"), "JPY"), "JPYOK → USLAX", "V001")));

        handler.on(event);

        var quotCaptor = ArgumentCaptor.forClass(QuotationRecord.class);
        verify(mapper).insertQuotation(quotCaptor.capture());
        assertThat(quotCaptor.getValue().getQuotationId()).isEqualTo(event.quotationId());
        assertThat(quotCaptor.getValue().getCargoType()).isEqualTo("GENERAL");

        var candCaptor = ArgumentCaptor.forClass(QuotationCandidateRecord.class);
        verify(mapper).insertCandidate(candCaptor.capture());
        assertThat(candCaptor.getValue().getCandidateSeq()).isEqualTo(1);
    }

    @Test
    @DisplayName("複数候補がある場合、シーケンス番号が連番で投影される")
    void 複数候補がシーケンス連番で投影される() {
        var event = generalEvent(List.of(
                new RouteCandidate(14, new Money(new BigDecimal("100000"), "JPY"), "JPYOK → USLAX", "V001"),
                new RouteCandidate(20, new Money(new BigDecimal("90000"), "JPY"), "JPYOK → TWKHH → USLAX", "V002")));

        handler.on(event);

        var candCaptor = ArgumentCaptor.forClass(QuotationCandidateRecord.class);
        verify(mapper, times(2)).insertCandidate(candCaptor.capture());
        assertThat(candCaptor.getAllValues().get(0).getCandidateSeq()).isEqualTo(1);
        assertThat(candCaptor.getAllValues().get(1).getCandidateSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("候補が空の場合、quotation のみ投影され candidate は投影されない")
    void 候補なし見積は本体のみ投影される() {
        var event = generalEvent(List.of());

        handler.on(event);

        verify(mapper).insertQuotation(org.mockito.ArgumentMatchers.any());
        verify(mapper, times(0)).insertCandidate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("HAZARDOUS 貨物の場合、危険物情報が quotation に投影される")
    void 危険物情報が投影される() {
        var hazard = new HazardInfo("3", "UN1203", "引火性液体");
        var event = hazardousEvent(hazard);

        handler.on(event);

        var captor = ArgumentCaptor.forClass(QuotationRecord.class);
        verify(mapper).insertQuotation(captor.capture());
        assertThat(captor.getValue().getHazardImoClass()).isEqualTo("3");
        assertThat(captor.getValue().getHazardUnNumber()).isEqualTo("UN1203");
    }

    private QuotationCreatedEvent generalEvent(List<RouteCandidate> candidates) {
        return new QuotationCreatedEvent(
                UUID.randomUUID().toString(),
                new ShipperId(1L),
                new RouteSpecification(Location.of("JPYOK"), Location.of("USLAX"),
                        LocalDate.now().plusDays(30)),
                CargoType.GENERAL,
                new BigDecimal("100"),
                null,
                new Money(new BigDecimal("100000"), "JPY"),
                candidates,
                LocalDate.now().plusDays(7),
                QuotationStatus.OFFERED);
    }

    private QuotationCreatedEvent hazardousEvent(HazardInfo hazard) {
        return new QuotationCreatedEvent(
                UUID.randomUUID().toString(),
                new ShipperId(1L),
                new RouteSpecification(Location.of("JPYOK"), Location.of("USLAX"),
                        LocalDate.now().plusDays(30)),
                CargoType.HAZARDOUS,
                new BigDecimal("100"),
                hazard,
                new Money(new BigDecimal("130000"), "JPY"),
                List.of(new RouteCandidate(14, new Money(new BigDecimal("130000"), "JPY"), "JPYOK → USLAX", "V001")),
                LocalDate.now().plusDays(7),
                QuotationStatus.OFFERED);
    }
}
