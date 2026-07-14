namespace CargoTracker.Billing.Domain.Model;

/// <summary>
/// 輸送基本料金の算出ドメインサービス（US21・スタブ）。重量（kg）と貨物種別から基本料金を算出する。
/// 将来の料金表・外部連携時はアダプター差し替えを想定した固定係数の暫定実装。金額は JPY・最小通貨単位（円）。
/// </summary>
public static class FreightCalculator
{
    private const long _baseRatePerKg = 100;      // 1kg あたり 100 円（暫定）
    private const string _currency = "JPY";

    /// <summary>貨物種別ごとの割増係数（危険物・冷凍は割増）。</summary>
    private static decimal CargoTypeMultiplier(string cargoType) => cargoType.ToUpperInvariant() switch
    {
        "HAZARDOUS" => 1.5m,
        "REFRIGERATED" => 1.3m,
        _ => 1.0m,          // General
    };

    /// <summary>重量・貨物種別から輸送基本料金を算出する。</summary>
    public static Money CalculateBaseAmount(decimal weight, string cargoType)
    {
        if (weight <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(weight), weight, "重量は正の値でなければなりません。");
        }
        var amount = (long)Math.Round(weight * _baseRatePerKg * CargoTypeMultiplier(cargoType), MidpointRounding.ToEven);
        return new Money(amount, _currency);
    }
}
