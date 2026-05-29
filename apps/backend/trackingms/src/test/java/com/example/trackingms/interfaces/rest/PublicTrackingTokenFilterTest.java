package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.model.TokenRole;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.model.VerifiedToken;
import com.example.trackingms.domain.services.TrackingTokenInvalidException;
import com.example.trackingms.domain.services.TrackingTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PublicTrackingTokenFilter} の単体テスト（US18 / ADR-0013 / IT6 タスク 1.3）。
 *
 * <p>servlet path、token クエリパラメータ、TrackingTokenService の検証成否の各組み合わせで
 * filter の振る舞いを検証する。403 Forbidden 応答時は Problem Detail JSON を返すことも確認する。</p>
 */
class PublicTrackingTokenFilterTest {

    private static final String VALID_PATH = "/api/v1/public/tracking/TRK-AB12CD3456";
    private static final TrackingNumber VALID_TN = TrackingNumber.of("TRK-AB12CD3456");

    private TrackingTokenService tokenService;
    private PublicTrackingTokenFilter filter;

    @BeforeEach
    void setUp() {
        tokenService = mock(TrackingTokenService.class);
        filter = new PublicTrackingTokenFilter(tokenService);
    }

    @Test
    @DisplayName("US18: 有効なトークンを持つリクエストは filter チェーンを通過する")
    void 有効なトークンは通過する() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VALID_PATH);
        request.setParameter("token", "valid-jwt-stub");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.verify(eq("valid-jwt-stub"), any(TrackingNumber.class)))
                .thenReturn(new VerifiedToken(VALID_TN, "shipper-001", TokenRole.SHIPPER,
                        LocalDateTime.now().plusDays(30)));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("ADR-0013: token クエリパラメータが欠落していれば 403 Forbidden")
    void トークン欠落は403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VALID_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getContentAsString()).contains("token");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("ADR-0013: token が空文字でも 403 Forbidden")
    void 空文字tokenは403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VALID_PATH);
        request.setParameter("token", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("ADR-0013: TrackingTokenInvalidException が出ると 403 Forbidden + 失敗 detail")
    void 不正トークンは403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VALID_PATH);
        request.setParameter("token", "expired-jwt-stub");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.verify(eq("expired-jwt-stub"), any(TrackingNumber.class)))
                .thenThrow(new TrackingTokenInvalidException("トークンの有効期限が切れています"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("無効または期限切れ");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("US18: /api/v1/public/tracking 配下以外のパスは shouldNotFilter で素通り")
    void 非対象パスは素通り() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/tracking/TRK-AB12CD3456");
        request.setParameter("token", "irrelevant");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("US18: 追跡番号のパス値が tokenService.verify() に渡される")
    void パス変数の追跡番号がverifyに渡される() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VALID_PATH);
        request.setParameter("token", "valid-jwt-stub");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        when(tokenService.verify(eq("valid-jwt-stub"), any(TrackingNumber.class)))
                .thenReturn(new VerifiedToken(VALID_TN, "shipper-001", TokenRole.SHIPPER,
                        LocalDateTime.now().plusDays(30)));

        filter.doFilter(request, response, chain);

        verify(tokenService).verify("valid-jwt-stub", VALID_TN);
    }
}
