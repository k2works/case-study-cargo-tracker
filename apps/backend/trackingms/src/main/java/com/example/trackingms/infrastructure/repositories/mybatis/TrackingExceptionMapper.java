package com.example.trackingms.infrastructure.repositories.mybatis;

import com.example.trackingms.domain.projections.TrackingExceptionView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 追跡例外 Read Model 用の MyBatis Mapper（US19 / US20 / IT6 タスク 2.3）。
 *
 * <p>SQL は {@code resources/mapper/TrackingExceptionMapper.xml} で定義する。</p>
 */
@Mapper
public interface TrackingExceptionMapper {

    /** 例外登録（TrackingExceptionRegisteredEvent で呼び出される）。 */
    void insertException(@Param("exceptionId") String exceptionId,
                         @Param("trackingNumber") String trackingNumber,
                         @Param("exceptionType") String exceptionType,
                         @Param("occurredAt") LocalDateTime occurredAt,
                         @Param("occurredUnlocode") String occurredUnlocode,
                         @Param("description") String description,
                         @Param("escalated") boolean escalated);

    /** 例外解決（TrackingExceptionResolvedEvent で呼び出される）。 */
    void markResolved(@Param("exceptionId") String exceptionId,
                      @Param("resolution") String resolution,
                      @Param("resolvedAt") LocalDateTime resolvedAt);

    /** 単一例外の取得（id 指定）。 */
    TrackingExceptionView findById(@Param("exceptionId") String exceptionId);

    /** 特定追跡番号の例外一覧（occurredAt DESC、ダッシュボード表示）。 */
    List<TrackingExceptionView> findByTrackingNumber(
            @Param("trackingNumber") String trackingNumber);

    /**
     * 全例外を横断検索（S19 例外対応一覧、フィルタ付き）。
     * responseStatus が null ならば全件、指定があればその状態のみ。
     * escalation 中（escalated=true）を優先表示するため、escalation・occurredAt の順でソート。
     */
    List<TrackingExceptionView> findAll(
            @Param("responseStatus") String responseStatus,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long count(@Param("responseStatus") String responseStatus);
}
