package com.example.routingms.application;

import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.infrastructure.repositories.mybatis.VoyageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoyageQueryServiceTest {

    @Mock
    private VoyageMapper voyageMapper;

    @InjectMocks
    private VoyageQueryService service;

    private VoyageProjection projection;

    @BeforeEach
    void setUp() {
        projection = new VoyageProjection();
        projection.setVoyageNumber("V001");
        projection.setCarrierCode("CARRIER-A");
    }

    @Test
    void 全航海を取得できる() {
        when(voyageMapper.findAll()).thenReturn(List.of(projection));

        List<VoyageProjection> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVoyageNumber()).isEqualTo("V001");
        verify(voyageMapper).findAll();
    }

    @Test
    void 航海番号で航海を取得できる() {
        when(voyageMapper.findByVoyageNumber("V001")).thenReturn(projection);

        VoyageProjection result = service.findByVoyageNumber("V001");

        assertThat(result.getVoyageNumber()).isEqualTo("V001");
        verify(voyageMapper).findByVoyageNumber("V001");
    }

    @Test
    void 存在しない航海番号はnullを返す() {
        when(voyageMapper.findByVoyageNumber("UNKNOWN")).thenReturn(null);

        VoyageProjection result = service.findByVoyageNumber("UNKNOWN");

        assertThat(result).isNull();
    }

    @Test
    void 検索条件を指定して航海を検索できる() {
        LocalDateTime from = LocalDateTime.of(2026, 6, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 6, 30, 23, 59);
        VoyageSearchCriteria criteria =
                new VoyageSearchCriteria("JPTYO", "USNYC", from, to, "GENERAL");
        when(voyageMapper.search("JPTYO", "USNYC", from, to, "GENERAL"))
                .thenReturn(List.of(projection));

        List<VoyageProjection> result = service.search(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVoyageNumber()).isEqualTo("V001");
        verify(voyageMapper).search("JPTYO", "USNYC", from, to, "GENERAL");
    }

    @Test
    void 条件に合致する航海がない場合は空リストを返す() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 31, 23, 59);
        VoyageSearchCriteria criteria =
                new VoyageSearchCriteria("JPOSA", "SGSIN", from, to, "HAZARDOUS");
        when(voyageMapper.search("JPOSA", "SGSIN", from, to, "HAZARDOUS"))
                .thenReturn(List.of());

        List<VoyageProjection> result = service.search(criteria);

        assertThat(result).isEmpty();
    }
}
