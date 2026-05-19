package com.example.cargotracker.trackingms.domain.model.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.trackingms.domain.model.valueobjects.JwtToken;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.domain.model.valueobjects.VerifiedToken;
import com.example.cargotracker.trackingms.infrastructure.security.JwtTrackingTokenService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TrackingTokenService} と {@link JwtTrackingTokenService} 実装の振る舞い検証（ADR-0013）。
 *
 * <p>HMAC-SHA256 / exp = 発行時 + 30 日 / 失効 = min(exp, deliveredAt + grace)。</p>
 */
@DisplayName("TrackingTokenService")
class TrackingTokenServiceTest {

    private static final String SECRET =
            "test-secret-please-change-in-production-must-be-at-least-256-bits-of-entropy";
    private static final TrackingNumber TRK = new TrackingNumber("TRK-20260101-ABC12345");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 20, 9, 0);

    private TrackingTokenService createService(LocalDateTime now) {
        Clock fixed = Clock.fixed(
                now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new JwtTrackingTokenService(SECRET, "case-study-cargo-tracker",
                "tracking-public-link", 30, 7, fixed);
    }

    @Test
    @DisplayName("発行した JWT は同じ秘密鍵で検証できる")
    void issueAndVerify_正常系() {
        var service = createService(NOW);
        JwtToken token = service.issue(TRK, null);

        VerifiedToken verified = service.verify(token.token(), null);

        assertThat(verified.trackingNumber()).isEqualTo(TRK);
        assertThat(verified.expiresAt()).isEqualTo(NOW.plusDays(30));
        assertThat(token.validUntil()).isEqualTo(NOW.plusDays(30));
    }

    @Test
    @DisplayName("delivered_at から 7 日以内であれば検証成功")
    void verify_配送後7日以内() {
        var issueService = createService(NOW);
        JwtToken token = issueService.issue(TRK, null);

        var deliveredAt = NOW.plusDays(20);
        // 配送 6 日後（delivered_at + 7d まで有効）
        var verifyService = createService(deliveredAt.plusDays(6));
        VerifiedToken verified = verifyService.verify(token.token(), deliveredAt);

        assertThat(verified.trackingNumber()).isEqualTo(TRK);
    }

    @Test
    @DisplayName("delivered_at から 7 日経過後は TOKEN_EXPIRED")
    void verify_配送後7日経過で失効() {
        var issueService = createService(NOW);
        String jwtString = issueService.issue(TRK, null).token();

        var deliveredAt = NOW.plusDays(10);
        var verifyService = createService(deliveredAt.plusDays(8));

        assertThatThrownBy(() -> verifyService.verify(jwtString, deliveredAt))
                .isInstanceOf(TrackingTokenExpiredException.class);
    }

    @Test
    @DisplayName("exp 経過後は TOKEN_EXPIRED")
    void verify_exp経過で失効() {
        var issueService = createService(NOW);
        String jwtString = issueService.issue(TRK, null).token();

        var verifyService = createService(NOW.plusDays(31));

        assertThatThrownBy(() -> verifyService.verify(jwtString, null))
                .isInstanceOf(TrackingTokenExpiredException.class);
    }

    @Test
    @DisplayName("H-10 境界値: exp 直前（1 秒前）は検証成功する")
    void verify_exp直前は有効() {
        var issueService = createService(NOW);
        String jwtString = issueService.issue(TRK, null).token();

        // exp = NOW + 30 日。その 1 秒前
        var verifyService = createService(NOW.plusDays(30).minusSeconds(1));

        VerifiedToken verified = verifyService.verify(jwtString, null);
        assertThat(verified.trackingNumber()).isEqualTo(TRK);
    }

    @Test
    @DisplayName("H-10 境界値: delivered_at + 7 日直前は検証成功する")
    void verify_配送後7日直前は有効() {
        var issueService = createService(NOW);
        String jwtString = issueService.issue(TRK, null).token();

        var deliveredAt = NOW.plusDays(10);
        // delivered_at + 7 日のちょうど 1 秒前
        var verifyService = createService(deliveredAt.plusDays(7).minusSeconds(1));

        VerifiedToken verified = verifyService.verify(jwtString, deliveredAt);
        assertThat(verified.trackingNumber()).isEqualTo(TRK);
    }

    @Test
    @DisplayName("H-10 境界値: delivered_at + 7 日経過直後（1 秒後）で TOKEN_EXPIRED")
    void verify_配送後7日経過直後で失効() {
        var issueService = createService(NOW);
        String jwtString = issueService.issue(TRK, null).token();

        var deliveredAt = NOW.plusDays(10);
        // delivered_at + 7 日 + 1 秒
        var verifyService = createService(deliveredAt.plusDays(7).plusSeconds(1));

        assertThatThrownBy(() -> verifyService.verify(jwtString, deliveredAt))
                .isInstanceOf(TrackingTokenExpiredException.class);
    }

    @Test
    @DisplayName("署名が異なる秘密鍵で検証すると TOKEN_INVALID")
    void verify_署名改ざんを検出() {
        var service = createService(NOW);
        String jwtString = service.issue(TRK, null).token();

        var otherService = new JwtTrackingTokenService(
                "different-secret-different-different-different-different-different-x",
                "case-study-cargo-tracker", "tracking-public-link", 30, 7,
                Clock.fixed(Instant.parse("2026-07-20T09:00:00Z"), ZoneId.systemDefault()));

        assertThatThrownBy(() -> otherService.verify(jwtString, null))
                .isInstanceOf(InvalidTrackingTokenException.class);
    }

    @Test
    @DisplayName("不正な形式の文字列を渡すと TOKEN_INVALID")
    void verify_不正な形式を検出() {
        var service = createService(NOW);

        assertThatThrownBy(() -> service.verify("not.a.jwt", null))
                .isInstanceOf(InvalidTrackingTokenException.class);
    }

    @Test
    @DisplayName("issue で deliveredAt を渡すと validUntil は min(exp, deliveredAt+grace)")
    void issue_配送済みの場合validUntilが短縮される() {
        var service = createService(NOW);
        var deliveredAt = NOW.plusDays(5);
        JwtToken token = service.issue(TRK, deliveredAt);

        assertThat(token.validUntil()).isEqualTo(deliveredAt.plusDays(7));
    }
}
