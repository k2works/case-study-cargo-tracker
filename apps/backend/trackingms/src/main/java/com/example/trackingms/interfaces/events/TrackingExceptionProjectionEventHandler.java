package com.example.trackingms.interfaces.events;

import com.example.trackingms.domain.events.TrackingExceptionRegisteredEvent;
import com.example.trackingms.domain.events.TrackingExceptionResolvedEvent;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingExceptionMapper;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 追跡例外 Read Model 更新用の EventHandler（US19 / US20 / IT6 タスク 2.3）。
 *
 * <p>{@link TrackingExceptionRegisteredEvent} と {@link TrackingExceptionResolvedEvent} を購読して
 * {@code tracking_exception} テーブルを 1:1 で射影する。state 遷移ガードは集約側で済んでいるため
 * 本ハンドラは SQL の単純な insert / update のみ実行する。</p>
 *
 * <p>ADR-0014 命名規約に従い prefix {@code local-} を採用（同サービス内のローカルイベント購読）。
 * 既存 {@code tracking-local-projection} と並ぶ位置付け。</p>
 */
@Component
@ProcessingGroup("local-tracking-exception-projection")
public class TrackingExceptionProjectionEventHandler {

    private final TrackingExceptionMapper exceptionMapper;

    public TrackingExceptionProjectionEventHandler(TrackingExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    @EventHandler
    public void on(TrackingExceptionRegisteredEvent event) {
        exceptionMapper.insertException(
                event.exceptionId(),
                event.trackingNumber(),
                event.type().name(),
                event.occurredAt(),
                event.occurredUnlocode(),
                event.description(),
                event.escalated()
        );
    }

    @EventHandler
    public void on(TrackingExceptionResolvedEvent event) {
        exceptionMapper.markResolved(
                event.exceptionId(),
                event.resolution(),
                event.resolvedAt()
        );
    }
}
