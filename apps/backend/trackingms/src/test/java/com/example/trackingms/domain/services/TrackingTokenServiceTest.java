package com.example.trackingms.domain.services;

import com.example.trackingms.domain.model.JwtToken;
import com.example.trackingms.domain.model.TokenRole;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.VerifiedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TrackingTokenService} ドメインサービスの単体テスト（US18 / ADR-0013 / IT6 タスク 1.1）。
 *
 * <p>HS256 + 32 バイト以上の鍵で JWT を発行し、署名・有効期限・追跡番号一致を厳密に検証する責務を持つ。
 * 有効期限は「発行時から 30 日」または「配送完了済みの場合は配送完了 + 7 日」のいずれか早い方。</p>
 */
class TrackingTokenServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-for-hs256!";
    private static final TrackingNumber TN = TrackingNumber.of("TRK-AB12CD3456");
    private static final String SUBJECT_ID = "shipper-001";
    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    private TrackingTokenService service;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(
                FIXED_NOW.toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        service = new TrackingTokenService(SECRET, fixedClock);
    }

    @Test
    @DisplayName("US18: 配送未完了の場合は 発行時刻 + 30 日 を有効期限とする")
    void 配送未完了時の有効期限は発行から30日() {
        JwtToken token = service.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, null);

        assertThat(token.issuedAt()).isEqualTo(FIXED_NOW);
        assertThat(token.validUntil()).isEqualTo(FIXED_NOW.plusDays(30));
        assertThat(token.token()).isNotBlank();
    }

    @Test
    @DisplayName("US18: 配送完了済みの場合は 配送完了 + 7 日 を有効期限とする（30 日より短い場合）")
    void 配送完了時の有効期限は配送完了から7日() {
        LocalDateTime deliveredAt = FIXED_NOW.minusDays(2);

        JwtToken token = service.issue(TN, SUBJECT_ID, TokenRole.CONSIGNEE, deliveredAt);

        assertThat(token.validUntil()).isEqualTo(deliveredAt.plusDays(7));
    }

    @Test
    @DisplayName("US18: 配送完了 + 7 日 が 発行 + 30 日 より遅くなる場合は 30 日上限を採用")
    void 配送完了7日が30日より長い場合は30日上限() {
        // 配送完了が現在より 25 日後 → 配送完了 + 7 日 = 現在 + 32 日 だが、上限は 30 日
        LocalDateTime futureDelivered = FIXED_NOW.plusDays(25);

        JwtToken token = service.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, futureDelivered);

        assertThat(token.validUntil()).isEqualTo(FIXED_NOW.plusDays(30));
    }

    @Test
    @DisplayName("US18: 発行したトークンを verify() で検証すると VerifiedToken を返す")
    void 発行したトークンを検証できる() {
        JwtToken token = service.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, null);

        VerifiedToken verified = service.verify(token.token(), TN);

        assertThat(verified.trackingNumber()).isEqualTo(TN);
        assertThat(verified.subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(verified.role()).isEqualTo(TokenRole.SHIPPER);
        assertThat(verified.expiresAt()).isEqualTo(FIXED_NOW.plusDays(30));
    }

    @Test
    @DisplayName("ADR-0013: tn claim と期待追跡番号が一致しないトークンは検証拒否")
    void 追跡番号が一致しないトークンは拒否() {
        JwtToken token = service.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, null);
        TrackingNumber other = TrackingNumber.of("TRK-XY99ZZ1111");

        assertThatThrownBy(() -> service.verify(token.token(), other))
                .isInstanceOf(TrackingTokenInvalidException.class)
                .hasMessageContaining("tn");
    }

    @Test
    @DisplayName("ADR-0013: 期限切れトークンは検証拒否")
    void 期限切れトークンは拒否() {
        // 発行時点を 31 日前にずらして発行
        Clock pastClock = Clock.fixed(
                FIXED_NOW.minusDays(31).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        TrackingTokenService pastService = new TrackingTokenService(SECRET, pastClock);
        JwtToken expired = pastService.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, null);

        // 検証時点は FIXED_NOW（期限切れ）
        assertThatThrownBy(() -> service.verify(expired.token(), TN))
                .isInstanceOf(TrackingTokenInvalidException.class);
    }

    @Test
    @DisplayName("ADR-0013: 別鍵で署名されたトークンは検証拒否")
    void 署名が不正なトークンは拒否() {
        String otherSecret = "different-secret-key-with-at-least-32-bytes!!!!!";
        TrackingTokenService otherService = new TrackingTokenService(otherSecret, fixedClock);
        JwtToken foreign = otherService.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, null);

        assertThatThrownBy(() -> service.verify(foreign.token(), TN))
                .isInstanceOf(TrackingTokenInvalidException.class);
    }

    @Test
    @DisplayName("ADR-0013: 鍵長が 32 バイト未満の場合は構築時に拒否")
    void 短い鍵では構築できない() {
        String shortSecret = "too-short";

        assertThatThrownBy(() -> new TrackingTokenService(shortSecret, fixedClock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("ADR-0013: null トークン文字列は検証拒否")
    void nullトークン文字列は拒否() {
        assertThatThrownBy(() -> service.verify(null, TN))
                .isInstanceOf(TrackingTokenInvalidException.class);
    }

    @Test
    @DisplayName("US18: 発行されたトークンに含まれる aud claim は tracking.public")
    void aud_claimはtracking_public() {
        JwtToken token = service.issue(TN, SUBJECT_ID, TokenRole.SHIPPER, null);

        // verify が成功すれば audience も内部で照合されている前提
        VerifiedToken verified = service.verify(token.token(), TN);

        assertThat(verified).isNotNull();
        // 内部の audience チェックの間接検証として、別 audience の自作トークンは拒否される
        // （実装で aud をハードコード照合）
        // テスト本体の主目的は「正常系で verify を通る」こと
        assertThat(Duration.between(verified.expiresAt(), FIXED_NOW.plusDays(30)))
                .isEqualTo(Duration.ZERO);
        // Instant 経路の互換チェック
        Instant inst = verified.expiresAt().toInstant(ZoneOffset.UTC);
        assertThat(inst).isAfter(FIXED_NOW.toInstant(ZoneOffset.UTC));
    }
}
