package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.model.aggregates.CancellationRequest;

/**
 * キャンセル申請の結果（US30-2・US30-3）。
 *
 * <p><strong>承認を待つかどうかを、呼び出し側に判断させない。</strong>画面が予約の状態を
 * 見比べて「輸送中だから承認待ち」と組み立てると、規則が集約・ユースケース・画面の
 * 3 か所に分かれる。
 *
 * @param request 作られた申請
 * @param awaitingApproval 追跡管理者の承認を待つか。false なら即時に確定した
 */
public record CancellationOutcome(CancellationRequest request, boolean awaitingApproval) {
}
