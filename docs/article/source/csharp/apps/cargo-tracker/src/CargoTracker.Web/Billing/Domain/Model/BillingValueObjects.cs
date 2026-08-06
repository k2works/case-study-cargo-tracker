namespace CargoTracker.Billing.Domain.Model;

/// <summary>精算書 ID（Billing BC 固有）。精算書を一意に識別する業務番号（INV-...）。</summary>
public sealed record InvoiceId
{
    public string Value { get; }

    public InvoiceId(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("精算書番号は必須です。", nameof(value));
        }
        Value = value;
    }

    /// <summary>予約番号を基に精算書番号を採番する（INV-<予約番号>）。</summary>
    public static InvoiceId Generate(string bookingId)
    {
        if (string.IsNullOrWhiteSpace(bookingId))
        {
            throw new ArgumentException("予約番号は必須です。", nameof(bookingId));
        }
        var suffix = bookingId.Replace("BKG-", string.Empty, StringComparison.OrdinalIgnoreCase);
        return new InvoiceId($"INV-{suffix}");
    }

    public override string ToString() => Value;
}

/// <summary>予約参照 ID（Billing BC 固有）。Booking Context との関連を保持する（BC 独立）。</summary>
public sealed record BillingBookingId
{
    public string Value { get; }

    public BillingBookingId(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("予約番号は必須です。", nameof(value));
        }
        Value = value;
    }

    public override string ToString() => Value;
}

/// <summary>荷主参照（Billing BC 固有・ACL 変換）。荷主種別を保持し法人判定を担う（BC 独立）。</summary>
public sealed record BillingShipperId
{
    public string Value { get; }
    public string ShipperType { get; }

    public BillingShipperId(string value, string shipperType)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("荷主 ID は必須です。", nameof(value));
        }
        Value = value;
        ShipperType = shipperType ?? string.Empty;
    }

    /// <summary>法人荷主かどうか（割引適用の対象判定）。</summary>
    public bool IsCorporate() => string.Equals(ShipperType, "Corporate", StringComparison.OrdinalIgnoreCase);
}

/// <summary>割引率（Billing BC 固有・0.0000〜0.3000＝最大 30%）。個人荷主は 0。domain-model BR5。</summary>
public sealed record DiscountRate
{
    public decimal Value { get; }

    public DiscountRate(decimal value)
    {
        if (value is < 0.0000m or > 0.3000m)
        {
            throw new ArgumentOutOfRangeException(nameof(value), value, "割引率は 0〜30% の範囲でなければなりません。");
        }
        Value = value;
    }

    public static DiscountRate Zero => new(0m);
}

/// <summary>支払い状態（Billing BC）。</summary>
public enum PaymentStatus
{
    Pending,
    Confirmed,
    Overdue,
    Refunded,
}
