package com.example.trackingms.application.outboundservices.notification;

import com.example.trackingms.domain.model.TransportStatus;

/**
 * 荷主・荷受人への通知 Port（domain-model.md M7 / IT5 0.6）。
 *
 * <p>本イテレーション（IT5）ではログ出力スタブとして実装する（{@code LoggingNotificationAcl}）。
 * 実メール送信は IT6 以降で外部連携 ADR とともに実装する。</p>
 *
 * <p>US14（採番完了）・US15（状態自動更新）・US17（手動更新）の受入基準
 * 「荷主への通知が送信される」をスタブ呼び出しで満たす設計。</p>
 */
public interface NotificationAcl {

    /**
     * 追跡番号の発行を荷主に通知する（US14）。
     *
     * @param trackingNumber 採番された追跡番号
     * @param bookingId      予約識別子
     */
    void notifyTrackingIssued(String trackingNumber, String bookingId);

    /**
     * 貨物状態の変更を荷主に通知する（US15 / US17）。
     *
     * @param trackingNumber 追跡番号
     * @param fromStatus     遷移前の状態（任意。初期化直後など null 可）
     * @param toStatus       遷移後の状態
     * @param unlocode       現在地（任意）
     */
    void notifyStatusChanged(String trackingNumber,
                             TransportStatus fromStatus,
                             TransportStatus toStatus,
                             String unlocode);

    /**
     * 誤配送検知を荷主・追跡管理者に通知する（US17）。
     *
     * @param trackingNumber 追跡番号
     * @param unlocode       誤配送が検知された場所（任意）
     */
    void notifyMisrouted(String trackingNumber, String unlocode);

    // --- IT6 タスク 2.5 / 3.2：US19 / US20 例外通知 ---

    /**
     * 追跡例外の発生を荷主に通知する（US19 / US20 受入基準 3/4）。
     *
     * @param trackingNumber   追跡番号
     * @param exceptionId      例外識別子
     * @param exceptionType    例外種別（DELAY / DAMAGE / LOSS）
     * @param occurredUnlocode 発生場所（任意）
     * @param description      発生状況・理由
     */
    void notifyExceptionRegistered(String trackingNumber,
                                   String exceptionId,
                                   String exceptionType,
                                   String occurredUnlocode,
                                   String description);

    /**
     * 追跡例外の解決を荷主に通知する（US19 受入基準 4）。
     *
     * @param trackingNumber 追跡番号
     * @param exceptionId    例外識別子
     * @param resolution     対応内容
     */
    void notifyExceptionResolved(String trackingNumber,
                                 String exceptionId,
                                 String resolution);

    /**
     * 追跡例外（紛失）の escalation を管理職に通知する（US20 受入基準 3）。
     *
     * @param trackingNumber 追跡番号
     * @param exceptionId    例外識別子
     * @param exceptionType  例外種別（実用上は LOSS）
     */
    void notifyExceptionEscalation(String trackingNumber,
                                   String exceptionId,
                                   String exceptionType);
}
