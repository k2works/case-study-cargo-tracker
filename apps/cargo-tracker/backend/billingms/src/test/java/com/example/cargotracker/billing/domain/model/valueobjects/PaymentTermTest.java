package com.example.cargotracker.billing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 支払条件（不変条件 3・4）。
 *
 * <p><b>Java 側の判定はここ 1 か所</b>にあり、集約（{@code Invoice#overdue}）と
 * 一覧の行（{@code InvoiceQueryHandler#toSummary}）の両方が読む。ここが正しければ
 * 両方が正しい——<b>逆に、ここを迂回して判定を書いたら意味が無い</b>。</p>
 *
 * <p>絞り込みは SQL にも同じ規則があるので、境界は
 * {@code InvoiceProjectionIT#doesNotListTheInvoiceOnItsDueDate} が別に固定する。</p>
 */
class PaymentTermTest {

    private static final LocalDate ISSUED_ON = LocalDate.of(2026, 10, 12);
    private static final LocalDate DUE_ON = LocalDate.of(2026, 11, 11);

    @Test
    @DisplayName("支払期限は発行日 + 30 日（不変条件 3）")
    void computesTheDueDate() {
        assertThat(PaymentTerm.dueOn(ISSUED_ON)).isEqualTo(DUE_ON);
        assertThat(PaymentTerm.DAYS).isEqualTo(30);
    }

    @Nested
    @DisplayName("期限超過の判定")
    class Overdue {

        @Test
        @DisplayName("請求済で期限の翌日から超過になる")
        void isOverdueTheDayAfterTheDueDate() {
            assertThat(PaymentTerm.overdue(BillingStatus.INVOICED, DUE_ON, DUE_ON.plusDays(1)))
                    .isTrue();
        }

        @Test
        @DisplayName("期限当日は超過ではない（当日中の入金はふつうにある）")
        void isNotOverdueOnTheDueDate() {
            assertThat(PaymentTerm.overdue(BillingStatus.INVOICED, DUE_ON, DUE_ON))
                    .as("<= にすると、期限内の荷主に督促が飛ぶ")
                    .isFalse();
            assertThat(PaymentTerm.overdue(BillingStatus.INVOICED, DUE_ON, DUE_ON.minusDays(1)))
                    .isFalse();
        }

        @Test
        @DisplayName("請求済でなければ超過は無い（未発行・入金済・取消）")
        void isNotOverdueInOtherStates() {
            LocalDate past = DUE_ON.plusDays(10);
            assertThat(PaymentTerm.overdue(BillingStatus.CALCULATED, DUE_ON, past))
                    .as("未発行の請求書に「期限を過ぎた」は無い")
                    .isFalse();
            assertThat(PaymentTerm.overdue(BillingStatus.PAID, DUE_ON, past))
                    .as("決着したものを督促しない")
                    .isFalse();
            assertThat(PaymentTerm.overdue(BillingStatus.VOID, DUE_ON, past)).isFalse();
        }

        @Test
        @DisplayName("期限も「今日」も欠けていれば超過にしない（落ちない）")
        void isNotOverdueWithoutDates() {
            // 列が無かったころの行は期限を持たない。**例外にすると一覧が開けなくなる。**
            assertThat(PaymentTerm.overdue(BillingStatus.INVOICED, null, DUE_ON)).isFalse();
            assertThat(PaymentTerm.overdue(BillingStatus.INVOICED, DUE_ON, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("状態の述語")
    class Predicates {

        @Test
        @DisplayName("発行できるのは算出済だけ")
        void acceptsIssueOnlyWhenCalculated() {
            assertThat(BillingStatus.CALCULATED.acceptsIssue()).isTrue();
            assertThat(BillingStatus.INVOICED.acceptsIssue()).isFalse();
            assertThat(BillingStatus.VOID.acceptsIssue())
                    .as("取り消した請求書は再発行しない（不変条件 6）")
                    .isFalse();
        }

        @Test
        @DisplayName("入金を記録できるのは請求済だけ")
        void acceptsPaymentOnlyWhenInvoiced() {
            assertThat(BillingStatus.INVOICED.acceptsPayment()).isTrue();
            assertThat(BillingStatus.CALCULATED.acceptsPayment())
                    .as("発行していない請求書への入金は「何に対する入金か」が決まらない")
                    .isFalse();
            assertThat(BillingStatus.PAID.acceptsPayment()).isFalse();
        }

        @Test
        @DisplayName("取り消せるのは算出済と請求済（入金済は決着している）")
        void acceptsVoidBeforeSettlement() {
            assertThat(BillingStatus.CALCULATED.acceptsVoid()).isTrue();
            assertThat(BillingStatus.INVOICED.acceptsVoid()).isTrue();
            assertThat(BillingStatus.PAID.acceptsVoid())
                    .as("決着したものを動かすと、入金の事実と請求書の状態が食い違う")
                    .isFalse();
            assertThat(BillingStatus.VOID.acceptsVoid()).isFalse();
        }

        @Test
        @DisplayName("決着したのは入金済と取消（一覧の既定から外す）")
        void isSettledForPaidAndVoid() {
            assertThat(BillingStatus.PAID.isSettled()).isTrue();
            assertThat(BillingStatus.VOID.isSettled()).isTrue();
            assertThat(BillingStatus.CALCULATED.isSettled())
                    .as("外すものを増やすと、まだ手を入れる場所が一覧から消える")
                    .isFalse();
            assertThat(BillingStatus.INVOICED.isSettled()).isFalse();
            assertThat(BillingStatus.PENDING.isSettled()).isFalse();
        }
    }
}
