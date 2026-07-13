namespace CargoTracker.Booking.Domain.Model;

/// <summary>
/// 確定経路の荷主通知記録（US12）。営業担当者が予約の確定経路を荷主に通知した事実を保持する。
/// 通知内容の要約（到着予定時刻）を伴う。
/// </summary>
public sealed class RouteNotification
{
    public BookingId BookingId { get; }
    public DateTimeOffset NotifiedAt { get; }
    public DateTimeOffset ExpectedArrivalTime { get; }

    private RouteNotification(BookingId bookingId, DateTimeOffset notifiedAt, DateTimeOffset expectedArrivalTime)
    {
        BookingId = bookingId;
        NotifiedAt = notifiedAt;
        ExpectedArrivalTime = expectedArrivalTime;
    }

    /// <summary>予約の確定経路（旅程）をもとに通知記録を生成する（US12）。</summary>
    public static RouteNotification Create(Cargo cargo, DateTimeOffset notifiedAt)
    {
        ArgumentNullException.ThrowIfNull(cargo);
        if (cargo.BookingStatus != BookingStatus.RouteProposed)
        {
            throw new InvalidOperationException("経路提案中の予約のみ荷主に通知できます。");
        }
        if (cargo.CargoItinerary is null)
        {
            throw new InvalidOperationException("経路が紐付けられていない予約は通知できません。");
        }
        return new RouteNotification(cargo.BookingId, notifiedAt, cargo.CargoItinerary.ExpectedArrivalTime);
    }

    /// <summary>永続化データから再構築する。</summary>
    public static RouteNotification Reconstruct(BookingId bookingId, DateTimeOffset notifiedAt, DateTimeOffset expectedArrivalTime)
        => new(bookingId, notifiedAt, expectedArrivalTime);
}
