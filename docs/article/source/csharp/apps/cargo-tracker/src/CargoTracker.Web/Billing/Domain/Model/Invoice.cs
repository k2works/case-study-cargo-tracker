using CargoTracker.Billing.Domain.Events;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Billing.Domain.Model;

/// <summary>
/// 精算書（US21-23）。配送完了した予約の輸送料金を算出し、法人割引を適用して最終金額を確定、
/// 精算書を発行して入金確認まで管理する集約ルート。基本料金・割引率・最終金額の一貫性を保証する。
/// </summary>
public sealed class Invoice : AggregateRoot
{
    public InvoiceId InvoiceId { get; }
    public BillingBookingId BookingId { get; }
    public BillingShipperId ShipperId { get; }
    public Money BaseAmount { get; private set; }
    public DiscountRate DiscountRate { get; private set; }
    public Money FinalAmount { get; private set; }
    public PaymentStatus PaymentStatus { get; private set; }
    public DateTimeOffset IssuedAt { get; }
    public DateTimeOffset DueDate { get; }
    public DateTimeOffset? PaidAt { get; private set; }
    public long Version { get; private set; }

    private Invoice(
        InvoiceId invoiceId, BillingBookingId bookingId, BillingShipperId shipperId,
        Money baseAmount, DiscountRate discountRate, Money finalAmount,
        PaymentStatus paymentStatus, DateTimeOffset issuedAt, DateTimeOffset dueDate,
        DateTimeOffset? paidAt, long version)
    {
        InvoiceId = invoiceId;
        BookingId = bookingId;
        ShipperId = shipperId;
        BaseAmount = baseAmount;
        DiscountRate = discountRate;
        FinalAmount = finalAmount;
        PaymentStatus = paymentStatus;
        IssuedAt = issuedAt;
        DueDate = dueDate;
        PaidAt = paidAt;
        Version = version;
    }

    /// <summary>
    /// 精算書を発行する（US21）。基本料金と発行日時・支払期限を与え、割引未適用の初期状態で作成する。
    /// 発行時点では最終金額 = 基本料金（割引は ApplyDiscount で適用）。
    /// </summary>
    public static Invoice Issue(
        string bookingId, BillingShipperId shipperId, Money baseAmount,
        DateTimeOffset issuedAt, DateTimeOffset dueDate)
    {
        ArgumentNullException.ThrowIfNull(baseAmount);
        var invoiceId = InvoiceId.Generate(bookingId);
        return new Invoice(
            invoiceId, new BillingBookingId(bookingId), shipperId,
            baseAmount, DiscountRate.Zero, baseAmount,
            PaymentStatus.Pending, issuedAt, dueDate, null, 0);
    }

    /// <summary>
    /// 法人割引を適用し最終金額を再計算する（US22）。個人荷主（IsCorporate=false）では割引率 0 とし基本料金のまま。
    /// 割引率は 0〜30%（DiscountRate で検証）。銀行家丸めで最小通貨単位へ丸める。
    /// </summary>
    public void ApplyDiscount(DiscountRate discountRate)
    {
        ArgumentNullException.ThrowIfNull(discountRate);
        DiscountRate = ShipperId.IsCorporate() ? discountRate : DiscountRate.Zero;
        FinalAmount = BaseAmount.ApplyDiscountRate(DiscountRate.Value);
        Version++;
    }

    /// <summary>割引額（基本料金 - 最終金額）を返す（精算明細の割引根拠用）。</summary>
    public Money DiscountAmount()
        => new(BaseAmount.Amount - FinalAmount.Amount, BaseAmount.Currency);

    /// <summary>入金を確認し精算済へ遷移する（US23）。Pending からのみ可。</summary>
    public void ConfirmPayment(DateTimeOffset paidAt)
    {
        if (PaymentStatus is not (PaymentStatus.Pending or PaymentStatus.Overdue))
        {
            throw new InvalidOperationException("未払い（Pending/Overdue）の精算書のみ入金確認できます。");
        }
        PaymentStatus = PaymentStatus.Confirmed;
        PaidAt = paidAt;
        Version++;
        AddDomainEvent(new PaymentConfirmedEvent(BookingId.Value, InvoiceId.Value, paidAt));
    }

    /// <summary>支払期限を超過した未払いを延滞（Overdue）にする（US23）。</summary>
    public void MarkOverdue(DateTimeOffset asOf)
    {
        if (PaymentStatus == PaymentStatus.Pending && asOf > DueDate)
        {
            PaymentStatus = PaymentStatus.Overdue;
            Version++;
        }
    }

    /// <summary>永続化データから再構築する。</summary>
    public static Invoice Reconstruct(
        InvoiceId invoiceId, BillingBookingId bookingId, BillingShipperId shipperId,
        Money baseAmount, DiscountRate discountRate, Money finalAmount,
        PaymentStatus paymentStatus, DateTimeOffset issuedAt, DateTimeOffset dueDate,
        DateTimeOffset? paidAt, long version)
        => new(invoiceId, bookingId, shipperId, baseAmount, discountRate, finalAmount,
            paymentStatus, issuedAt, dueDate, paidAt, version);
}
