package com.example.billingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("請求書の検索条件")
class InvoiceSearchCriteriaTest {

    @Test
    @DisplayName("何も指定しなければ、絞り込みは無い")
    void emptyCriteriaFiltersNothing() {
        InvoiceSearchCriteria criteria = InvoiceSearchCriteria.of(null, null);

        assertThat(criteria.keyword()).isEmpty();
        assertThat(criteria.issuedMonth()).isEmpty();
    }

    /**
     * <strong>空白は「指定なし」として扱う。</strong>入力欄を消したつもりの空白で
     * 「何にも一致しない検索」になると、経理は「1 件も無い」と読む。
     */
    @Test
    @DisplayName("空白だけの語は、指定なしとして扱う")
    void blankKeywordIsNoFilter() {
        assertThat(InvoiceSearchCriteria.of("   ", null).keyword()).isEmpty();
    }

    @Test
    @DisplayName("前後の空白は落とす")
    void trimsTheKeyword() {
        assertThat(InvoiceSearchCriteria.of("  伊藤商事 ", null).keyword()).contains("伊藤商事");
    }

    @Test
    @DisplayName("発行月を指定できる")
    void keepsTheIssuedMonth() {
        InvoiceSearchCriteria criteria =
                InvoiceSearchCriteria.of(null, YearMonth.of(2026, 9));

        assertThat(criteria.issuedMonth()).contains(YearMonth.of(2026, 9));
    }

    /**
     * <strong>締めの月は未来にならない。</strong>打ち間違いをそのまま検索すると
     * 「0 件」が返り、経理は「その月は発行していない」と読む。
     */
    @Test
    @DisplayName("発行月の形式が不正なら、その場で断る")
    void rejectsMalformedMonth() {
        assertThatThrownBy(() -> InvoiceSearchCriteria.parse("keyword", "2026-13"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("発行月");
    }

    @Test
    @DisplayName("文字列の発行月を読み取れる")
    void parsesTheMonth() {
        assertThat(InvoiceSearchCriteria.parse(null, "2026-09").issuedMonth())
                .contains(YearMonth.of(2026, 9));
    }
}
