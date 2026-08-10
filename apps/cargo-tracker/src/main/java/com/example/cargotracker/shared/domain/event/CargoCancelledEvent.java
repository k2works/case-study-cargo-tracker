package com.example.cargotracker.shared.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 輸送中の予約キャンセルが承認された（US30）。
 *
 * <p><strong>同期のポートにしない</strong>（ADR-021）。承認するのは追跡管理者、
 * 請求するのは経理担当者である。<strong>承認画面の前にいる人は
 * キャンセル料について何もできない</strong>ため、その場で結果を返す必要が無い。
 *
 * <p><strong>Booking から Billing を呼ばない</strong>（ADR-012）。運ぶのは起きた事実で
 * あり命令ではない。「キャンセル料を請求せよ」ではなく「キャンセルされた」を伝え、
 * <strong>金額をいくらにするかは Billing が決める</strong>
 * （金額の正典は Billing にある。Booking から金額を送ると基準額が 2 つ生まれる）。
 *
 * <p><strong>料率は運ぶ。</strong> 率は「どこまで手配を進めていたか」に対する
 * 業務の判断であり、Booking の持ち物である。<strong>申請時点の率</strong>を運ぶ —
 * 承認が遅れたことの費用を荷主に負わせない。
 *
 * @param bookingId         予約 ID
 * @param feeRate           キャンセル料の料率（<strong>申請時点</strong>）
 * @param dischargeUnlocode 陸揚げ地の UN/LOCODE
 * @param approvedBy        承認した追跡管理者
 * @param approvedAt        承認日時
 */
public record CargoCancelledEvent(
        UUID bookingId,
        BigDecimal feeRate,
        String dischargeUnlocode,
        String approvedBy,
        Instant approvedAt) {
}
