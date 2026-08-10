package com.example.cargotracker.billing.application.internal.outboundservices.acl;

import java.time.Instant;
import java.util.List;

/**
 * 貨物に起きた例外を読む出力ポート（Billing → Tracking の ACL。IT13 レビュー C3）。
 *
 * <p><strong>料金調整の根拠は例外の記録である</strong>（US21 の受入基準 6）。
 * 請求書の画面には「例外あり」しか出ておらず、
 * <strong>いくら減額するかを決める人が、何が起きたのかをその場で読めない</strong>。
 * 別の画面を開いて追跡番号で探し直す間に、経理担当者は「たぶん遅延だろう」で
 * 金額を決めてしまう。<strong>根拠が同じ画面に無い調整は、根拠の無い調整になる。</strong>
 *
 * <p><strong>Booking の {@code CargoExceptions} を使い回さない。</strong> あれは
 * Booking の出力ポートであり、Billing から参照すると BC をまたぐ
 * （ArchUnit ルール 4）。<strong>ポートは使う側の BC が持つ</strong>（ADR-012）。
 *
 * <p>運ぶのは<strong>表示のための素の値だけ</strong>である（ADR-005）。
 */
public interface CargoExceptionRecordsPort {

    /**
     * 追跡番号から例外を引く（<strong>読み取り専用</strong>）。
     *
     * <p>並び順は<strong>発生の新しい順</strong>。
     *
     * @param trackingNumber 追跡番号
     * @return 例外が無ければ空のリスト。<strong>形式の違う番号でも空</strong>
     *         （請求書の画面が 500 になってはならない）
     */
    List<ExceptionRecord> findByTrackingNumber(String trackingNumber);

    /**
     * 例外 1 件（表示用）。
     *
     * @param typeLabel   例外種別の表示名
     * @param occurredAt  発生日時
     * @param description 状況。<strong>減額の金額を決める材料である</strong>
     * @param resolved    対応済か。<strong>片づいた話と現在進行中の話は別である</strong>
     */
    record ExceptionRecord(
            String typeLabel, Instant occurredAt, String description, boolean resolved) {
    }
}
