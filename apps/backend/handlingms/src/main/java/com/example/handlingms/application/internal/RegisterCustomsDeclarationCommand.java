package com.example.handlingms.application.internal;

import java.time.Instant;

/**
 * 通関申告の登録（US29-1）。
 *
 * <p><strong>起点は追跡番号である。</strong>荷役作業員は予約番号を知らない
 * （[ADR-023] 決定 2 と同じ立場）。予約 ID は ACL の写しから引く。
 *
 * <p><strong>状態を受け取らない。</strong>初期状態は集約が決める——登録の時点で
 * 通関済を選べると、引取のガードが最初から素通りになる。
 *
 * @param trackingNumber 追跡番号
 * @param declarationNumber 申告番号（税関から受け取る業務キー）
 * @param declaredAt 申告日時
 * @param remarks 備考。無ければ null
 * @param declaredBy 登録した利用者
 */
public record RegisterCustomsDeclarationCommand(String trackingNumber, String declarationNumber,
        Instant declaredAt, String remarks, String declaredBy) {
}
