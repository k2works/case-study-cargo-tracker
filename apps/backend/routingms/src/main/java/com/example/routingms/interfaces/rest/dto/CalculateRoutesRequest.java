package com.example.routingms.interfaces.rest.dto;

import java.time.LocalDate;

/**
 * 経路候補算出の条件調整リクエスト（US10、任意）。
 *
 * <p>ボディ無し（または {@code arrivalDeadline} 無し）の場合は経路設計依頼に登録された到着期限で算出する（US08）。
 * {@code arrivalDeadline} を指定すると、その期限で再算出する（US10 条件調整：期限延長）。</p>
 */
public record CalculateRoutesRequest(LocalDate arrivalDeadline) {
}
