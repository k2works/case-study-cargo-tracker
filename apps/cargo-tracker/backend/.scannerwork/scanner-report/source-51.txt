package com.example.cargotracker.booking.infrastructure.persistence;

import com.example.cargotracker.booking.application.port.ProcessState;
import com.example.cargotracker.booking.application.port.ProcessState.Status;
import com.example.cargotracker.booking.application.port.ProcessStateService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** {@link ProcessStateService} の実装。状態は process_state テーブルに持つ。 */
@Service
public class MyBatisProcessStateService implements ProcessStateService {

    private static final Logger log = LoggerFactory.getLogger(MyBatisProcessStateService.class);

    private final ProcessStateMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MyBatisProcessStateService(ProcessStateMapper mapper, ObjectMapper objectMapper,
            Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProcessState start(String processType, String processId, String firstStep,
            int totalSteps, Map<String, String> metadata) {
        ProcessStateMapper.ProcessStateRow existing = mapper.findById(processType, processId);
        if (existing != null) {
            // イベントは再配信されうる。作り直すと進んだ段が巻き戻る。
            log.debug("連鎖は既に始まっている: {}/{}", processType, processId);
            return toModel(existing);
        }
        Instant now = clock.instant();
        ProcessStateMapper.ProcessStateRow row = new ProcessStateMapper.ProcessStateRow(
                processType, processId, firstStep, totalSteps, 0, Status.RUNNING.name(),
                toJson(metadata), now, now, null);
        mapper.insert(row);
        return toModel(row);
    }

    @Override
    public Optional<ProcessState> find(String processType, String processId) {
        return Optional.ofNullable(mapper.findById(processType, processId)).map(this::toModel);
    }

    @Override
    @Transactional
    public ProcessState advance(String processType, String processId, String completedStep,
            String nextStep) {
        ProcessStateMapper.ProcessStateRow row = require(processType, processId);
        ProcessState current = toModel(row);

        if (!current.isRunning()) {
            // 終わった連鎖に遅れて届いたイベント。進めると完了が取り消される。
            log.debug("実行中でない連鎖は進めない: {}/{} status={}", processType, processId,
                    current.status());
            return current;
        }
        if (!completedStep.equals(current.currentStep())) {
            // 同じ段を 2 度受け取った、または順序が入れ替わった。進めると段が飛ぶ。
            log.debug("段が一致しないので進めない: {}/{} current={} received={}",
                    processType, processId, current.currentStep(), completedStep);
            return current;
        }

        Instant now = clock.instant();
        int completedSteps = current.completedSteps() + 1;
        boolean done = completedSteps >= current.totalSteps();
        ProcessStateMapper.ProcessStateRow updated = new ProcessStateMapper.ProcessStateRow(
                processType, processId,
                done ? completedStep : nextStep,
                current.totalSteps(), completedSteps,
                (done ? Status.COMPLETED : Status.RUNNING).name(),
                toJson(current.metadata()), current.startedAt(), now, done ? now : null);
        mapper.update(updated);
        return toModel(updated);
    }

    @Override
    @Transactional
    public ProcessState compensate(String processType, String processId, String reason) {
        ProcessStateMapper.ProcessStateRow row = require(processType, processId);
        ProcessState current = toModel(row);

        Map<String, String> metadata = new LinkedHashMap<>(current.metadata());
        metadata.put("compensationReason", reason);

        Instant now = clock.instant();
        ProcessStateMapper.ProcessStateRow updated = new ProcessStateMapper.ProcessStateRow(
                processType, processId, current.currentStep(), current.totalSteps(),
                current.completedSteps(), Status.COMPENSATED.name(), toJson(metadata),
                current.startedAt(), now, now);
        mapper.update(updated);
        return toModel(updated);
    }

    @Override
    public List<ProcessState> findStuck(String processType, Duration olderThan) {
        return mapper.findStuck(processType, clock.instant().minus(olderThan)).stream()
                .map(this::toModel)
                .toList();
    }

    private ProcessStateMapper.ProcessStateRow require(String processType, String processId) {
        ProcessStateMapper.ProcessStateRow row = mapper.findById(processType, processId);
        if (row == null) {
            // 黙って作ると、始まっていない連鎖が進んだことになる。
            throw new IllegalStateException(
                    "連鎖が見つかりません: " + processType + "/" + processId);
        }
        return row;
    }

    private ProcessState toModel(ProcessStateMapper.ProcessStateRow row) {
        return new ProcessState(row.processType(), row.processId(), row.currentStep(),
                row.totalSteps(), row.completedSteps(), Status.valueOf(row.status()),
                fromJson(row.metadata()), row.startedAt(), row.updatedAt(), row.completedAt());
    }

    private String toJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(metadata);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, Map.class);
    }
}
