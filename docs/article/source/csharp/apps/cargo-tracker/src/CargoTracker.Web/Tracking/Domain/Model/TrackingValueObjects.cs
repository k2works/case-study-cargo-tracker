namespace CargoTracker.Tracking.Domain.Model;

/// <summary>追跡番号（Tracking BC 固有）。追跡活動を一意に識別する。</summary>
public sealed record TrackingNumber
{
    public string Value { get; }

    public TrackingNumber(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("追跡番号は必須です。", nameof(value));
        }
        if (value.Length > 20)
        {
            throw new ArgumentException("追跡番号は 20 文字以内で指定してください。", nameof(value));
        }
        Value = value.Trim().ToUpperInvariant();
    }

    /// <summary>予約番号を基に追跡番号を採番する（TRK-<予約番号>）。</summary>
    public static TrackingNumber Generate(string bookingId)
    {
        if (string.IsNullOrWhiteSpace(bookingId))
        {
            throw new ArgumentException("予約番号は必須です。", nameof(bookingId));
        }
        var suffix = bookingId.Replace("BKG-", string.Empty, StringComparison.OrdinalIgnoreCase);
        return new TrackingNumber($"TRK-{suffix}");
    }

    public override string ToString() => Value;
}

/// <summary>予約参照 ID（Tracking BC 固有）。Booking Context との関連を保持する（BC 独立）。</summary>
public sealed record TrackingBookingId
{
    public string Value { get; }

    public TrackingBookingId(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("予約番号は必須です。", nameof(value));
        }
        Value = value;
    }

    public override string ToString() => Value;
}

/// <summary>追跡位置情報（Tracking BC 固有・ACL 変換）。</summary>
public sealed record TrackingLocation(string UnLocode);

/// <summary>追跡航海番号（Tracking BC 固有）。</summary>
public sealed record TrackingVoyageNumber(string Number);

/// <summary>輸送状態（Tracking BC の追跡状態）。Booking/Handling と共有する 9 段階。</summary>
public enum TrackingStatus
{
    NotReceived,
    Received,
    Loaded,
    OnboardCarrier,
    Unloaded,
    AwaitingClaim,
    Claimed,
    Exception,
    Unknown,
}

/// <summary>追跡イベント種別（荷役種別に対応）。US15/US16 の荷役記録が追跡イベントを追記する。</summary>
public enum TrackingEventType
{
    Receive,
    Load,
    Unload,
    Claim,
}

/// <summary>
/// 例外種別（Tracking BC 固有・US19/US20）。遅延・破損・紛失・税関保留。
/// 本リリースでは Delay/Damage/Lost を扱い、CustomsHold は税関システム連携（対象外）で自動登録される。
/// </summary>
public enum ExceptionType
{
    Delay,
    Damage,
    Lost,
    CustomsHold,
}
