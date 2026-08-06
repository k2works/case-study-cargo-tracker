namespace CargoTracker.Tracking.Interfaces;

/// <summary>輸送状態（DB 表現）を日本語ラベルに変換する表示ヘルパー（US18）。</summary>
public static class TransportStatusLabel
{
    public static string ToJapanese(string status) => status switch
    {
        "NOT_RECEIVED" => "受領待ち",
        "RECEIVED" => "受領済",
        "LOADED" => "積込済",
        "ONBOARD_CARRIER" => "輸送中",
        "UNLOADED" => "荷降し済",
        "AWAITING_CLAIM" => "引取待ち",
        "CLAIMED" => "引取済",
        "EXCEPTION" => "例外発生",
        _ => status,
    };

    public static string EventTypeToJapanese(string eventType) => eventType.ToUpperInvariant() switch
    {
        "RECEIVE" => "受領",
        "LOAD" => "積込",
        "UNLOAD" => "荷降し",
        "CLAIM" => "引取",
        _ => eventType,
    };
}
