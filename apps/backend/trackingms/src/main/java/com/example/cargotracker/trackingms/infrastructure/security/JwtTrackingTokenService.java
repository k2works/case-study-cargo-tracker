package com.example.cargotracker.trackingms.infrastructure.security;

import com.example.cargotracker.trackingms.domain.model.services.InvalidTrackingTokenException;
import com.example.cargotracker.trackingms.domain.model.services.TrackingTokenExpiredException;
import com.example.cargotracker.trackingms.domain.model.services.TrackingTokenService;
import com.example.cargotracker.trackingms.domain.model.valueobjects.JwtToken;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * jjwt 0.12.x をベースとした {@link TrackingTokenService} の HMAC-SHA256 実装（ADR-0013）。
 *
 * <p>JWT クレーム:</p>
 *
 * <ul>
 *   <li>{@code tn}  - 追跡番号</li>
 *   <li>{@code exp} - 有効期限（発行時 + 30 日 等）</li>
 *   <li>{@code iss} - 発行者</li>
 *   <li>{@code sub} - 用途（{@code tracking-public-link}）</li>
 *   <li>{@code iat} - 発行時刻</li>
 * </ul>
 *
 * <p>{@link Clock} を注入することで、テスト時には固定時刻を使い時間進行をシミュレートする。</p>
 */
@Service
public final class JwtTrackingTokenService implements TrackingTokenService {

    private static final String CLAIM_TRACKING_NUMBER = "tn";

    private final SecretKey secretKey;
    private final String issuer;
    private final String subject;
    private final int expirationDays;
    private final int graceDays;
    private final Clock clock;

    @Autowired
    public JwtTrackingTokenService(
            @Value("${cargo-tracker.tracking.token.secret}") String secret,
            @Value("${cargo-tracker.tracking.token.issuer:case-study-cargo-tracker}") String issuer,
            @Value("${cargo-tracker.tracking.token.subject:tracking-public-link}") String subject,
            @Value("${cargo-tracker.tracking.token.expiration-days:30}") int expirationDays,
            @Value("${cargo-tracker.tracking.token.grace-days:7}") int graceDays) {
        this(secret, issuer, subject, expirationDays, graceDays, Clock.systemDefaultZone());
    }

    public JwtTrackingTokenService(
            String secret,
            String issuer,
            String subject,
            int expirationDays,
            int graceDays,
            Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.subject = subject;
        this.expirationDays = expirationDays;
        this.graceDays = graceDays;
        this.clock = clock;
    }

    @Override
    public JwtToken issue(TrackingNumber trackingNumber, LocalDateTime deliveredAt) {
        LocalDateTime issuedAt = LocalDateTime.now(clock);
        LocalDateTime exp = issuedAt.plusDays(expirationDays);
        LocalDateTime validUntil = (deliveredAt == null)
                ? exp
                : earliest(exp, deliveredAt.plusDays(graceDays));

        Date issuedAtDate = toDate(issuedAt);
        Date expDate = toDate(exp);

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAtDate)
                .expiration(expDate)
                .claim(CLAIM_TRACKING_NUMBER, trackingNumber.value())
                .signWith(secretKey)
                .compact();

        return new JwtToken(token, issuedAt, validUntil);
    }

    @Override
    public TrackingNumber verify(String token, LocalDateTime deliveredAt) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .requireSubject(subject)
                    .clock(this::nowAsDate)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException _) {
            throw new TrackingTokenExpiredException("JWT exp 経過");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTrackingTokenException("JWT 検証に失敗しました", e);
        }

        if (deliveredAt != null) {
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime cutoff = deliveredAt.plusDays(graceDays);
            if (now.isAfter(cutoff)) {
                throw new TrackingTokenExpiredException(
                        "配送完了から " + graceDays + " 日経過");
            }
        }

        String tn = claims.get(CLAIM_TRACKING_NUMBER, String.class);
        if (tn == null) {
            throw new InvalidTrackingTokenException("tn クレームが存在しません");
        }
        return new TrackingNumber(tn);
    }

    private Date nowAsDate() {
        return Date.from(Instant.now(clock));
    }

    private Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime earliest(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
