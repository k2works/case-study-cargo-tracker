package com.example.cargotracker.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.handling.domain.model.CorrectionRequest;
import com.example.cargotracker.handling.domain.model.CorrectionRequestType;
import com.example.cargotracker.handling.domain.model.CorrectionStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引取記録の訂正・取り消し申請（US36）。
 *
 * <p>引取は輸送の終点であり、<strong>誤登録をそのままにすると貨物が届いていないのに
 * 配送完了として扱われる</strong>。
 *
 * <p><strong>申請と承認を分ける意味は、別人であって初めて生まれる。</strong>
 * 一人で申請と承認ができるなら、承認という段階は形だけになる。
 */
@DisplayName("訂正・取り消し申請（US36）")
class CorrectionRequestTest {

    private static final Instant REQUESTED = Instant.parse("2026-04-21T10:00:00Z");
    private static final Instant DECIDED = Instant.parse("2026-04-21T11:00:00Z");

    private static CorrectionRequest 取り消しの申請() {
        return CorrectionRequest.request(
                1L, CorrectionRequestType.CANCEL, "貨物を取り違えて登録した",
                "handler1", REQUESTED);
    }

    /** 受入基準: 訂正または取り消しを申請できる（**理由は必須**）。 */
    @Test
    void 理由を添えて申請できる() {
        CorrectionRequest request = 取り消しの申請();

        assertThat(request.status()).isEqualTo(CorrectionStatus.PENDING);
        assertThat(request.reason()).isEqualTo("貨物を取り違えて登録した");
    }

    /**
     * <strong>理由の無い申請は受け付けない。</strong>
     *
     * <p>後から見ると「なぜ配送完了が取り消されたのか」が誰にも分からない。
     */
    @Test
    void 理由の無い申請は受け付けない() {
        for (String blank : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> CorrectionRequest.request(
                    1L, CorrectionRequestType.CANCEL, blank, "handler1", REQUESTED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("理由");
        }
    }

    /** 受入基準: 追跡管理者が承認すると、承認済みになる。 */
    @Test
    void 別の担当者が承認できる() {
        CorrectionRequest request = 取り消しの申請();

        request.approve("tracker1", DECIDED);

        assertThat(request.status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(request.decision().by()).isEqualTo("tracker1");
        assertThat(request.decision().at()).isEqualTo(DECIDED);
    }

    /**
     * <strong>申請した本人は承認できない。</strong>
     *
     * <p>これが無いと、承認の段階は形だけになる。受入基準
     * 「追跡管理者の承認なしには状態が戻らない」は<strong>別人であって初めて
     * 満たされる</strong>。
     */
    @Test
    void 申請した本人は承認できない() {
        CorrectionRequest request = 取り消しの申請();

        assertThatThrownBy(() -> request.approve("handler1", DECIDED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("申請した本人");
        assertThat(request.status())
                .as("拒まれた承認で状態が動いてはならない")
                .isEqualTo(CorrectionStatus.PENDING);
    }

    /**
     * <strong>二度は決められない。</strong>
     *
     * <p>決め直しを許すと、却下されたものを後から承認でき、
     * <strong>決定の日時と決定者が上書きされる</strong>。
     */
    @Test
    void 決まった申請を決め直せない() {
        CorrectionRequest request = 取り消しの申請();
        request.reject("tracker1", DECIDED, "登録内容に誤りは無い");

        assertThatThrownBy(() -> request.approve("tracker2", DECIDED.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(request.status()).isEqualTo(CorrectionStatus.REJECTED);
    }

    /** <strong>却下には理由が要る。</strong> 申請者は次に何をすればよいか分からない。 */
    @Test
    void 理由の無い却下はできない() {
        CorrectionRequest request = 取り消しの申請();

        assertThatThrownBy(() -> request.reject("tracker1", DECIDED, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("理由");
    }

    /**
     * <strong>取り消しと訂正で承認したときに起きることが違う。</strong>
     *
     * <p>取り消しは貨物状態を引取前に戻すが、訂正は記録の中身だけを直す。
     * 画面と処理は同じ述語を使う。
     */
    @Test
    void 取り消しだけが貨物状態を戻す() {
        assertThat(CorrectionRequestType.CANCEL.revertsCargoStatus()).isTrue();
        assertThat(CorrectionRequestType.CORRECT.revertsCargoStatus()).isFalse();
    }

    /** <strong>取り消しの申請に訂正内容は持たせない。</strong> 直す中身が無い。 */
    @Test
    void 取り消しの申請に訂正内容は指定できない() {
        CorrectionRequest request = 取り消しの申請();

        assertThatThrownBy(() -> request.correcting(DECIDED, "作業時刻の誤り"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 訂正では直す中身を伴う。**中身の無い訂正は申請にならない。** */
    @Test
    void 訂正には直す内容が要る() {
        CorrectionRequest correction = CorrectionRequest.request(
                1L, CorrectionRequestType.CORRECT, "作業時刻を誤って登録した",
                "handler1", REQUESTED);

        assertThatThrownBy(() -> correction.correcting(null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(correction.correcting(DECIDED, null).details().correctedCompletionTime())
                .isEqualTo(DECIDED);
    }
}
