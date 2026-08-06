namespace CargoTracker.Billing.Domain.Model;

/// <summary>
/// 金額（Billing BC 固有の値オブジェクト）。最小通貨単位（例: 円は 1、ドルはセント）を long で保持する。
/// 通貨コードが異なる金額の演算は許可しない。割引率等の乗算は最小通貨単位へ銀行家丸め（MidpointRounding.ToEven）する。
/// </summary>
public sealed record Money
{
    public long Amount { get; }
    public string Currency { get; }

    public Money(long amount, string currency)
    {
        if (amount < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(amount), amount, "金額は 0 以上でなければなりません。");
        }
        if (string.IsNullOrWhiteSpace(currency))
        {
            throw new ArgumentException("通貨コードは必須です。", nameof(currency));
        }
        Amount = amount;
        Currency = currency.ToUpperInvariant();
    }

    public static Money Zero(string currency) => new(0, currency);

    /// <summary>同一通貨の金額を加算する。</summary>
    public Money Add(Money other)
    {
        ArgumentNullException.ThrowIfNull(other);
        EnsureSameCurrency(other);
        return new Money(Amount + other.Amount, Currency);
    }

    /// <summary>金額に係数を乗じ、最小通貨単位へ銀行家丸め（ToEven）する（割引・税計算用）。</summary>
    public Money Multiply(decimal factor)
    {
        var scaled = (long)Math.Round(Amount * factor, MidpointRounding.ToEven);
        return new Money(scaled, Currency);
    }

    /// <summary>割引率を適用した割引後金額を返す（元金 × (1 - 割引率)）。</summary>
    public Money ApplyDiscountRate(decimal rate) => Multiply(1m - rate);

    private void EnsureSameCurrency(Money other)
    {
        if (Currency != other.Currency)
        {
            throw new InvalidOperationException($"通貨が一致しません（{Currency} と {other.Currency}）。");
        }
    }

    public override string ToString() => $"{Amount} {Currency}";
}
