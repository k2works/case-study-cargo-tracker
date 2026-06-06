package com.example.trackingms.domain.services;

import com.example.trackingms.domain.model.JwtToken;
import com.example.trackingms.domain.model.TokenRole;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.VerifiedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 公開追跡照会の時限署名トークンを発行・検証するドメインサービス（US18 / ADR-0013）。
 *
 * <p>HS256 + 32 バイト以上の鍵で JWT を発行し、署名・有効期限・追跡番号（{@code tn} claim）・
 * audience（{@code tracking.public}）の 4 項目を厳密に検証する。authms の {@code JwtTokenProvider}
 * とは <strong>別鍵</strong>で運用し、認証 JWT と公開照会 JWT を完全分離する（鍵漏洩時の影響範囲限定）。</p>
 *
 * <p>有効期限ロジック（ui_design.md L729 / L737 準拠）：</p>
 * <ul>
 *   <li>配送未完了：発行時刻 + 30 日</li>
 *   <li>配送完了済み：配送完了 + 7 日（ただし発行時刻 + 30 日を上限とする）</li>
 * </ul>
 *
 * <p>検証失敗時は全て {@link TrackingTokenInvalidException} に変換する。
 * Spring Security の {@code PublicTrackingTokenFilter} がこれを捕捉して HTTP 403 Forbidden を返す。</p>
 */
@Service
public class TrackingTokenService {

    private static final String ISSUER = "trackingms";
    private static final String AUDIENCE = "tracking.public";
    private static final String CLAIM_TRACKING_NUMBER = "tn";
    private static final String CLAIM_ROLE = "role";
    private static final int MAX_VALID_DAYS = 30;
    private static final int DELIVERED_GRACE_DAYS = 7;

    private final TrackingTokenSecretProvider secretProvider;
    private final Clock clock;

    public TrackingTokenService(TrackingTokenSecretProvider secretProvider, Clock clock) {
        this.secretProvider = secretProvider;
        this.clock = clock;
    }

    /**
     * 公開照会トークンを発行する。
     *
     * @param trackingNumber 追跡番号（{@code tn} claim に格納）
     * @param subjectId      利用主体 ID（荷主 ID or 荷受人 ID、{@code sub} claim に格納）
     * @param role           ロール（{@code role} claim に格納）
     * @param deliveredAt    配送完了時刻（null なら未配送扱い）
     * @return 発行された JWT と発行時刻・有効期限
     */
    public JwtToken issue(TrackingNumber trackingNumber, String subjectId, TokenRole role,
                          LocalDateTime deliveredAt) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expires = calculateExpiry(now, deliveredAt);

        String tokenStr = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(subjectId)
                .claim(CLAIM_TRACKING_NUMBER, trackingNumber.value())
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(toDate(now))
                .expiration(toDate(expires))
                .signWith(secretProvider.activeSigningKey())
                .compact();

        return new JwtToken(tokenStr, now, expires);
    }

    /**
     * 公開照会トークンを検証する。
     *
     * @param token                   検証対象の JWT 文字列
     * @param expectedTrackingNumber  パス変数から取得した追跡番号（{@code tn} claim と一致確認）
     * @return 検証に成功した場合は {@link VerifiedToken}
     * @throws TrackingTokenInvalidException 署名不正・期限切れ・追跡番号不一致・その他の検証失敗
     */
    public VerifiedToken verify(String token, TrackingNumber expectedTrackingNumber) {
        if (token == null || token.isBlank()) {
            throw new TrackingTokenInvalidException("token が空です");
        }
        // IT8 T1.6: 四半期ローテーション期間中は現行 + 旧 secret の両方で検証する。
        // 最初に成功した鍵で通す。すべて失敗した場合は最後の例外をそのまま伝播する。
        JwtException lastFailure = null;
        for (SecretKey key : secretProvider.verifyingKeys()) {
            try {
                return verifyWithKey(token, expectedTrackingNumber, key);
            } catch (ExpiredJwtException ex) {
                // 有効期限切れは secret によらず確定的なので即時失敗
                throw new TrackingTokenInvalidException("トークンの有効期限が切れています", ex);
            } catch (JwtException ex) {
                lastFailure = ex;
            }
        }
        throw new TrackingTokenInvalidException(
                "トークンの検証に失敗しました: "
                        + (lastFailure == null ? "no verifying key configured" : lastFailure.getMessage()),
                lastFailure);
    }

    /**
     * 単一の secret key でトークンを検証する。
     *
     * <p>本メソッドは確定的失敗（有効期限切れ・claim 不一致）の場合、verify ループに挽回させず
     * 即時に呼び出し元へ伝播させる設計。署名不一致などの JwtException はループに伝播し、
     * 次の secret key で再試行される。</p>
     */
    private VerifiedToken verifyWithKey(String token, TrackingNumber expectedTrackingNumber, SecretKey key) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .clock(() -> Date.from(LocalDateTime.now(clock).toInstant(ZoneOffset.UTC)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // jjwt の clock 注入で期限切れは ExpiredJwtException に変換されるが、
        // 二重防御として LocalDateTime ベースでも明示的に検証する。
        LocalDateTime expDt = LocalDateTime.ofInstant(
                claims.getExpiration().toInstant(), ZoneOffset.UTC);
        if (!expDt.isAfter(LocalDateTime.now(clock))) {
            throw new TrackingTokenInvalidException("トークンの有効期限が切れています");
        }

        String tn = claims.get(CLAIM_TRACKING_NUMBER, String.class);
        if (tn == null || !tn.equals(expectedTrackingNumber.value())) {
            throw new TrackingTokenInvalidException(
                    "tn claim がパス変数と一致しません（期待: " + expectedTrackingNumber.value()
                            + " / claim: " + tn + "）");
        }

        TokenRole role = parseRoleClaim(claims.get(CLAIM_ROLE, String.class));

        LocalDateTime exp = LocalDateTime.ofInstant(
                claims.getExpiration().toInstant(), ZoneOffset.UTC);

        return new VerifiedToken(expectedTrackingNumber, claims.getSubject(), role, exp);
    }

    /**
     * {@code role} claim の文字列を {@link TokenRole} 列挙へ変換する。
     * 不正値（列挙外・null）は {@link TrackingTokenInvalidException} に変換する。
     * 外側 try / catch とのネスト解消のため独立メソッドに切り出している（SonarQube java:S1141）。
     */
    private TokenRole parseRoleClaim(String roleStr) {
        try {
            return TokenRole.valueOf(roleStr);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new TrackingTokenInvalidException("role claim が不正です: " + roleStr, ex);
        }
    }

    private LocalDateTime calculateExpiry(LocalDateTime now, LocalDateTime deliveredAt) {
        LocalDateTime maxExpiry = now.plusDays(MAX_VALID_DAYS);
        if (deliveredAt == null) {
            return maxExpiry;
        }
        LocalDateTime deliveredCap = deliveredAt.plusDays(DELIVERED_GRACE_DAYS);
        return deliveredCap.isBefore(maxExpiry) ? deliveredCap : maxExpiry;
    }

    private static Date toDate(LocalDateTime dt) {
        return Date.from(dt.toInstant(ZoneOffset.UTC));
    }
}
