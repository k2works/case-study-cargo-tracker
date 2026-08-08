package com.example.cargotracker.tracking.application.internal.queryservices;

import java.time.Instant;
import java.util.List;

/**
 * 追跡照会の表示用データ（US18）。
 *
 * <p><strong>ここに個人情報を入れない。</strong> 同じ形を公開画面
 * （{@code /public/tracking}）でも使う。荷主名・住所・連絡先・担当者名を
 * 足した瞬間に、認証を持たない相手へ流れる。
 *
 * <p>足したくなったときは「この項目が取引先に転送された URL から見えてよいか」を
 * 先に問う。<strong>見えてよくないなら、公開画面と認証つき画面で別の型にする。</strong>
 *
 * @param trackingNumber   追跡番号
 * @param statusLabel      輸送状態の日本語ラベル
 * @param statusBadgeClass 輸送状態のバッジ（正典は {@code TransportStatus}）
 * @param currentLocation  現在地（{@code JPOSA（大阪）} 形式）。イベントが無ければ空文字
 * @param destination      目的地（{@code USLAX（ロサンゼルス）} 形式）
 * @param estimatedArrival 推定到着日。経路が未確定なら {@code null}。
 *                         <strong>日付である</strong>（ADR-012 で追跡が自分で持つ値にした）。
 *                         画面は「{@code YYYY-MM-DD 頃}」と出しており、時刻は使っていない
 * @param events           イベント履歴（新しい順）
 */
public record TrackingInquiryView(
        String trackingNumber,
        String statusLabel,
        String statusBadgeClass,
        String currentLocation,
        String destination,
        java.time.LocalDate estimatedArrival,
        List<TrackingEventView> events) {

    public TrackingInquiryView {
        events = List.copyOf(events == null ? List.of() : events);
    }

    /** 現在地が分かるか。未受取のうちはイベントが無く、現在地を答えられない。 */
    public boolean hasCurrentLocation() {
        return currentLocation != null && !currentLocation.isBlank();
    }

    /** 推定到着日が出せるか。経路が未確定なら画面は「未確定」と表示する。 */
    public boolean hasEstimatedArrival() {
        return estimatedArrival != null;
    }

    /**
     * イベント履歴の 1 件。
     *
     * @param occurredAt 発生日時
     * @param typeLabel  イベント種別の日本語ラベル
     * @param location   発生場所（{@code JPOSA（大阪）} 形式）
     */
    public record TrackingEventView(Instant occurredAt, String typeLabel, String location) {
    }
}
