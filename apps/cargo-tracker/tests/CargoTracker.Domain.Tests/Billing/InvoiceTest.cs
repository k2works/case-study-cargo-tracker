using CargoTracker.Billing.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Billing;

public class InvoiceTest
{
    private static readonly DateTimeOffset _issued = new(2026, 10, 1, 0, 0, 0, TimeSpan.Zero);
    private static readonly DateTimeOffset _due = new(2026, 10, 31, 0, 0, 0, TimeSpan.Zero);

    private static Invoice IssueFor(string shipperType) => Invoice.Issue(
        "BKG-INV-0001",
        new BillingShipperId("SHP-1", shipperType),
        new Money(10000, "JPY"), _issued, _due);

    [Fact]
    public void 精算書を発行すると基本料金と支払待ちで作成される()
    {
        var invoice = IssueFor("Individual");

        invoice.InvoiceId.Value.Should().Be("INV-INV-0001");
        invoice.BaseAmount.Should().Be(new Money(10000, "JPY"));
        invoice.FinalAmount.Should().Be(new Money(10000, "JPY"));
        invoice.PaymentStatus.Should().Be(PaymentStatus.Pending);
    }

    [Fact]
    public void 法人荷主には割引が適用される()
    {
        var invoice = IssueFor("Corporate");

        invoice.ApplyDiscount(new DiscountRate(0.10m));

        invoice.DiscountRate.Value.Should().Be(0.10m);
        invoice.FinalAmount.Should().Be(new Money(9000, "JPY"));
        invoice.DiscountAmount().Should().Be(new Money(1000, "JPY"));
    }

    [Fact]
    public void 個人荷主には割引が適用されない()
    {
        var invoice = IssueFor("Individual");

        invoice.ApplyDiscount(new DiscountRate(0.10m));

        invoice.DiscountRate.Value.Should().Be(0m);
        invoice.FinalAmount.Should().Be(new Money(10000, "JPY"));
    }

    [Fact]
    public void 割引率は30パーセントを超えられない()
    {
        var act = () => new DiscountRate(0.31m);

        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Fact]
    public void 入金確認で精算済へ遷移する()
    {
        var invoice = IssueFor("Corporate");
        var paidAt = new DateTimeOffset(2026, 10, 20, 0, 0, 0, TimeSpan.Zero);

        invoice.ConfirmPayment(paidAt);

        invoice.PaymentStatus.Should().Be(PaymentStatus.Confirmed);
        invoice.PaidAt.Should().Be(paidAt);
    }

    [Fact]
    public void 入金確認で入金確認イベントが発行される()
    {
        var invoice = IssueFor("Corporate");

        invoice.ConfirmPayment(new DateTimeOffset(2026, 10, 20, 0, 0, 0, TimeSpan.Zero));

        invoice.PullDomainEvents().Should().ContainSingle()
            .Which.Should().BeOfType<CargoTracker.Billing.Domain.Events.PaymentConfirmedEvent>();
    }

    [Fact]
    public void 支払済の精算書は再度入金確認できない()
    {
        var invoice = IssueFor("Corporate");
        invoice.ConfirmPayment(new DateTimeOffset(2026, 10, 20, 0, 0, 0, TimeSpan.Zero));

        var act = () => invoice.ConfirmPayment(new DateTimeOffset(2026, 10, 21, 0, 0, 0, TimeSpan.Zero));

        act.Should().Throw<InvalidOperationException>();
    }

    [Fact]
    public void 支払期限超過で延滞になる()
    {
        var invoice = IssueFor("Individual");

        invoice.MarkOverdue(new DateTimeOffset(2026, 11, 1, 0, 0, 0, TimeSpan.Zero));

        invoice.PaymentStatus.Should().Be(PaymentStatus.Overdue);
    }

    [Fact]
    public void 期限内は延滞にならない()
    {
        var invoice = IssueFor("Individual");

        invoice.MarkOverdue(new DateTimeOffset(2026, 10, 15, 0, 0, 0, TimeSpan.Zero));

        invoice.PaymentStatus.Should().Be(PaymentStatus.Pending);
    }
}
