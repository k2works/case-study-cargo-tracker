package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.model.TokenRole;

/**
 * 公開照会トークン発行リクエスト（US18 / ADR-0013）。
 *
 * @param subjectId 利用主体 ID（荷主 ID または荷受人 ID）。JWT の {@code sub} claim に格納される
 * @param role      ロール（SHIPPER or CONSIGNEE）。JWT の {@code role} claim に格納される
 */
public record IssueTokenRequest(String subjectId, TokenRole role) {
}
