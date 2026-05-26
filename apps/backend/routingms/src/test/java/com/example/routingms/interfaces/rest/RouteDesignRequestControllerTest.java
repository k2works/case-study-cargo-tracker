package com.example.routingms.interfaces.rest;

import com.example.routingms.application.RouteDesignRequestQueryService;
import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.interfaces.rest.dto.RouteDesignRequestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteDesignRequestControllerTest {

    @Mock
    private RouteDesignRequestQueryService queryService;

    @InjectMocks
    private RouteDesignRequestController controller;

    private RouteDesignRequestProjection makeProjection(String bookingId) {
        RouteDesignRequestProjection p = new RouteDesignRequestProjection();
        p.setBookingId(bookingId);
        p.setOriginUnlocode("JPTYO");
        p.setDestinationUnlocode("USNYC");
        p.setArrivalDeadline(LocalDate.of(2027, 9, 30));
        p.setCargoType("GENERAL");
        p.setStatus("PENDING");
        return p;
    }

    @Test
    void 予約IDで経路設計依頼を取得できる() {
        when(queryService.findByBookingId("BK-001")).thenReturn(makeProjection("BK-001"));

        ResponseEntity<RouteDesignRequestResponse> response = controller.findByBookingId("BK-001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().bookingId()).isEqualTo("BK-001");
        assertThat(response.getBody().originUnlocode()).isEqualTo("JPTYO");
        assertThat(response.getBody().destinationUnlocode()).isEqualTo("USNYC");
        assertThat(response.getBody().cargoType()).isEqualTo("GENERAL");
    }

    @Test
    void 存在しない予約IDは404が返る() {
        when(queryService.findByBookingId("UNKNOWN")).thenReturn(null);

        ResponseEntity<RouteDesignRequestResponse> response = controller.findByBookingId("UNKNOWN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 全経路設計依頼一覧を取得できる() {
        when(queryService.findAll())
                .thenReturn(List.of(makeProjection("BK-001"), makeProjection("BK-002")));

        ResponseEntity<List<RouteDesignRequestResponse>> response = controller.findAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(queryService).findAll();
    }
}
