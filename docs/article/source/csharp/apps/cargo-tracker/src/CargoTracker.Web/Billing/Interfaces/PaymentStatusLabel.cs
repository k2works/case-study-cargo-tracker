namespace CargoTracker.Billing.Interfaces;

/// <summary>支払い状態の DB 表現（SCREAMING_SNAKE）を日本語ラベルに変換する（US21-23）。</summary>
public static class PaymentStatusLabel
{
    public static string ToJapanese(string status) => status.ToUpperInvariant() switch
    {
        "PENDING" => "支払待ち",
        "CONFIRMED" => "精算済",
        "OVERDUE" => "延滞",
        "REFUNDED" => "返金済",
        _ => status,
    };
}
