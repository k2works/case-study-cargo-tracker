package com.example.routingms.application;

import com.example.routingms.domain.projections.RouteDesignRequestProjection;
import com.example.routingms.infrastructure.repositories.mybatis.RouteDesignRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteDesignRequestQueryServiceTest {

    @Mock
    private RouteDesignRequestMapper mapper;

    @InjectMocks
    private RouteDesignRequestQueryService service;

    private RouteDesignRequestProjection projection;

    @BeforeEach
    void setUp() {
        projection = new RouteDesignRequestProjection();
        projection.setBookingId("BK-001");
        projection.setOriginUnlocode("JPTYO");
        projection.setDestinationUnlocode("USNYC");
        projection.setArrivalDeadline(LocalDate.of(2027, 9, 30));
        projection.setCargoType("GENERAL");
        projection.setStatus("PENDING");
    }

    @Test
    void 予約IDで経路設計依頼を取得できる() {
        when(mapper.findByBookingId("BK-001")).thenReturn(projection);

        RouteDesignRequestProjection result = service.findByBookingId("BK-001");

        assertThat(result.getBookingId()).isEqualTo("BK-001");
        assertThat(result.getOriginUnlocode()).isEqualTo("JPTYO");
        verify(mapper).findByBookingId("BK-001");
    }

    @Test
    void 存在しない予約IDはnullを返す() {
        when(mapper.findByBookingId("UNKNOWN")).thenReturn(null);

        RouteDesignRequestProjection result = service.findByBookingId("UNKNOWN");

        assertThat(result).isNull();
    }

    @Test
    void 全経路設計依頼を取得できる() {
        when(mapper.findAll()).thenReturn(List.of(projection));

        List<RouteDesignRequestProjection> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBookingId()).isEqualTo("BK-001");
        verify(mapper).findAll();
    }
}
