namespace CargoTracker.Booking.Domain.Model;

/// <summary>
/// 貨物種別。Estimation にも同名 enum があるが、BC 独立を優先して Booking 独自に定義する。
/// 共通化は CargoType の変更頻度が見えた後に ADR で判断する。
/// </summary>
public enum CargoType
{
    General,
    Hazardous,
    Refrigerated,
}
