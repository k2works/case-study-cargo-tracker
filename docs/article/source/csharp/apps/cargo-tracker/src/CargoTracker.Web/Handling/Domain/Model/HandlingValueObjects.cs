using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Handling.Domain.Model;

/// <summary>貨物予約識別子（Handling BC 固有）。Booking Context との関連を保持する（BC 独立）。</summary>
public sealed record CargoBookingId
{
    public string Value { get; }

    public CargoBookingId(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("予約番号は必須です。", nameof(value));
        }
        Value = value;
    }

    public override string ToString() => Value;
}

/// <summary>荷役種別（Handling BC 固有）。VoyageNumber 必須判定を内包する。</summary>
public sealed record HandlingType
{
    public HandlingEventType Type { get; }

    public HandlingType(HandlingEventType type) => Type = type;

    /// <summary>積込・荷降しは航海番号が必須。</summary>
    public bool RequiresVoyageNumber() => Type is HandlingEventType.Load or HandlingEventType.Unload;

    public bool IsLoadType() => Type == HandlingEventType.Load;

    public bool IsClaimType() => Type == HandlingEventType.Claim;
}

/// <summary>荷役種別の列挙。CUSTOMS（通関）は本リリース対象外。</summary>
public enum HandlingEventType
{
    Receive,
    Load,
    Unload,
    Claim,
}

/// <summary>荷役航海番号（Handling BC 固有）。</summary>
public sealed record VoyageNumber(string Number);

/// <summary>旅程区間スナップショット（ACL 経由で取得した Booking の旅程情報）。</summary>
public sealed record LegSnapshot(string LoadLocationUnLocode, string UnloadLocationUnLocode, string VoyageNumber);

/// <summary>
/// 貨物スナップショット（ACL 経由で取得した Booking の貨物情報）。荷役の妥当性検証に使用する。
/// Booking の内部モデルに依存せず Handling 側プリミティブで保持する（BC 独立）。
/// </summary>
public sealed record CargoSnapshot(
    string BookingId,
    string Origin,
    string Destination,
    IReadOnlyList<LegSnapshot> ItineraryLegs);
