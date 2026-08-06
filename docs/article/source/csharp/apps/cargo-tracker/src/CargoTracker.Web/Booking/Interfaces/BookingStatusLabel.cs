namespace CargoTracker.Booking.Interfaces;

/// <summary>予約状態（DB 表現）を日本語ラベル・バッジ色に変換する表示ヘルパー（IT3 レビュー M1）。</summary>
public static class BookingStatusLabel
{
    public static string ToJapanese(string status) => status switch
    {
        "PRELIMINARY" => "仮受付",
        "ROUTE_PROPOSED" => "経路提案中",
        "CONFIRMED" => "予約確定",
        "TRACKING_ISSUED" => "追跡番号発行済",
        "IN_TRANSIT" => "輸送中",
        "DELIVERED" => "配送完了",
        "SETTLED" => "精算済",
        "CANCELLED" => "キャンセル",
        _ => status,
    };

    public static string ToBadgeColor(string status) => status switch
    {
        "PRELIMINARY" => "secondary",
        "ROUTE_PROPOSED" => "info",
        "CONFIRMED" => "success",
        "CANCELLED" => "danger",
        _ => "primary",
    };
}
