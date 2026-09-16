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
import org.springframework.transaction.annotation.Propagation;
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

    /**
     * <b>別トランザクションで書く。</b>
     *
     * <p>連鎖の 1 段目は「起票してからコマンドを送る」。送信が失敗すると Reaction
     * Handler は例外を投げ直して Event Processor に再試行させるが、<b>同じ
     * トランザクションだと起票も一緒に巻き戻る</b>。すると `process_state` に行が
     * 残らず、<b>止まった連鎖が滞留の走査に出ない</b>（IT8 H.1 でクラスタ実測。
     * trackingms を落としたら行が 1 つも作られなかった）。</p>
     *
     * <p>フェイクを使う単体テストではトランザクションの巻き戻りを再現しないので、
     * 判別できなかった。{@code AttentionItemRecorder} が同じ理由で
     * {@code REQUIRES_NEW} にしている。</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessState start(String processType, String processId, String firstStep,
            int totalSteps, Map<String, String> metadata) {
        Instant now = clock.instant();
        ProcessStateMapper.ProcessStateRow existing = mapper.findById(processType, processId);
        if (existing != null && Status.COMPENSATED.name().equals(existing.status())) {
            // **補償は行き止まりではない。** 要確認一覧は「追跡番号を発行し直せ」と
            // 言う（ADR-0010 決定 4）。発行し直すと新しいイベントが出るので、連鎖も
            // やり直せなければならない。やり直せないと、予約は TRACKING_ISSUED に
            // なるのに追跡が作られないまま残る（IT8 H.1 でクラスタ実測）。
            //
            // **回数は引き継がない。** 引き継ぐと 1 回の失敗で即座に補償に落ちる。
            // **やり直したことは残す。** 何度も落ちている予約を見つけられるように。
            Map<String, String> restarted = new LinkedHashMap<>(
                    metadata == null ? Map.of() : metadata);
            restarted.put("restartedAfterCompensation", now.toString());
            ProcessStateMapper.ProcessStateRow row = new ProcessStateMapper.ProcessStateRow(
                    processType, processId, firstStep, totalSteps, 0, Status.RUNNING.name(),
                    toJson(restarted), existing.startedAt(), now, null);
            mapper.update(row);
            log.info("補償した連鎖をやり直す: {}/{}", processType, processId);
            return toModel(row);
        }
        if (existing != null) {
            // イベントは再配信されうる。作り直すと進んだ段が巻き戻る。
            log.debug("連鎖は既に始まっている: {}/{}", processType, processId);
            return toModel(existing);
        }
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

    /**
     * <b>別トランザクションで書く。</b>
     *
     * <p>回数を記録するのは送信に失敗したときで、そのあと例外を投げ直す。同じ
     * トランザクションだと回数も巻き戻り、<b>永久に 1 のままで上限に到達しない</b>
     * ——trackingms が落ちている間、無限に再試行し続けることになる。</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessState recordAttempt(String processType, String processId, int attempts) {
        ProcessStateMapper.ProcessStateRow row = require(processType, processId);
        ProcessState current = toModel(row);

        Map<String, String> metadata = new LinkedHashMap<>(current.metadata());
        metadata.put("attempts", String.valueOf(attempts));

        ProcessStateMapper.ProcessStateRow updated = new ProcessStateMapper.ProcessStateRow(
                processType, processId, current.currentStep(), current.totalSteps(),
                current.completedSteps(), current.status().name(), toJson(metadata),
                current.startedAt(), clock.instant(), current.completedAt());
        mapper.update(updated);
        return toModel(updated);
    }

    /**
     * <b>別トランザクションで書く。</b>
     *
     * <p>補償したことは、外側が何を巻き戻しても残さなければならない。消えると
     * 「追跡番号が取り消された理由」を誰も説明できない。</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
