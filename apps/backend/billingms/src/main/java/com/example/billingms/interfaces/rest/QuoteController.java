package com.example.billingms.interfaces.rest;

import com.example.billingms.application.internal.QuoteChargeUseCase;
import com.example.shared.auth.AuthenticatedUser;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 料金の試算を返す（US01-3・[ADR-028] 決定 6）。
 *
 * <p><strong>呼ぶのは bookingms であり、人ではない。</strong>経理担当者の画面は
 * {@link BillingController} を使う。同じサービスに人向けと機械向けの入口が並ぶのは、
 * bookingms 側の {@code BillingLookupController} と同じ形である。
 *
 * <p><strong>これが本 IT で増えた結合方向である</strong>——bookingms → billingms。
 * 終盤で新しい結合方式を発明しないため、既存の ACL と同じ 4 点（専用の応答型・
 * 名簿方式の認可・契約フィクスチャ・両側の契約テスト）をそのまま踏む。
 */
@RestController
@RequestMapping("/api/v1/billing")
public class QuoteController {

    /**
     * この入口を呼んでよいサービス。
     *
     * <p><strong>名簿に無い主体は通さない</strong>（[ADR-015] 以来の許可リスト方式）。
     * 人のロールでも開かない——経理担当者は請求の画面を使う。
     */
    private static final Set<String> TRUSTED_SERVICE_PRINCIPALS = Set.of("system:bookingms");

    private final QuoteChargeUseCase quote;

    public QuoteController(QuoteChargeUseCase quote) {
        this.quote = quote;
    }

    /**
     * 経路の基本料金を試算する。
     *
     * <p><strong>保存しない。</strong>試算は請求ではない。
     */
    @PostMapping("/quotes")
    public QuoteResponse quote(
            @RequestHeader(AuthenticatedUser.USER_ID_HEADER) String userId,
            @RequestBody QuoteRequest request) {
        requireTrustedService(userId);

        try {
            return new QuoteResponse(MoneyResponse.from(quote.quote(
                    request.legs() == null ? null : request.legs().stream()
                            .map(leg -> new QuoteChargeUseCase.QuoteLeg(
                                    leg.loadRegion(), leg.unloadRegion()))
                            .toList(),
                    request.weightKg(), request.cargoType())));
        } catch (IllegalArgumentException invalid) {
            // 区間が無い・知らない区分や種別。**相手の入力の誤りであり 400 である**
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }

    private void requireTrustedService(String userId) {
        if (!AuthenticatedUser.of(userId, null).isOneOf(TRUSTED_SERVICE_PRINCIPALS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        }
    }
}
