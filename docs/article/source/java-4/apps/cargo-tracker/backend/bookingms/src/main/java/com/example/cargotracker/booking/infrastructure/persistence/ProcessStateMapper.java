package com.example.cargotracker.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 連鎖の途中経過（data-model.md「連鎖の途中経過」）。 */
@Mapper
public interface ProcessStateMapper {

    int insert(ProcessStateRow row);

    ProcessStateRow findById(@Param("processType") String processType,
            @Param("processId") String processId);

    int update(ProcessStateRow row);

    List<ProcessStateRow> findStuck(@Param("processType") String processType,
            @Param("updatedBefore") Instant updatedBefore);

    record ProcessStateRow(
            String processType,
            String processId,
            String currentStep,
            int totalSteps,
            int completedSteps,
            String status,
            String metadata,
            Instant startedAt,
            Instant updatedAt,
            Instant completedAt) {
    }
}
