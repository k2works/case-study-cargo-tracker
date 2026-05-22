package com.example.routingms.application;

import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.infrastructure.repositories.mybatis.VoyageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VoyageQueryService {

    private final VoyageMapper voyageMapper;

    public VoyageQueryService(VoyageMapper voyageMapper) {
        this.voyageMapper = voyageMapper;
    }

    public List<VoyageProjection> findAll() {
        return voyageMapper.findAll();
    }

    public VoyageProjection findByVoyageNumber(String voyageNumber) {
        return voyageMapper.findByVoyageNumber(voyageNumber);
    }
}
