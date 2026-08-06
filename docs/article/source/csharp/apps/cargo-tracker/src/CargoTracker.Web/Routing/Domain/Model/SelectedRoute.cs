using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Routing.Domain.Model;

/// <summary>確定経路の状態（US09）。</summary>
public enum RouteStatus
{
    /// <summary>経路候補から選択され確定した状態。</summary>
    Confirmed,
}

/// <summary>
/// 確定経路（US09）。経路設計者が経路候補（CandidateRoute）から選択・確定した経路を表す集約ルート。
/// 予約（BookingId）に対して 1 つの確定経路を持つ。US11 で Booking の旅程へ変換される。
/// </summary>
public sealed class SelectedRoute : AggregateRoot
{
    public string BookingId { get; }
    public CandidateRoute Route { get; }
    public RouteStatus Status { get; private set; }
    public long Version { get; private set; }

    private SelectedRoute(string bookingId, CandidateRoute route, RouteStatus status, long version)
    {
        BookingId = bookingId;
        Route = route;
        Status = status;
        Version = version;
    }

    /// <summary>経路候補を選択して確定経路を生成する（US09）。</summary>
    public static SelectedRoute Confirm(string bookingId, CandidateRoute route)
    {
        if (string.IsNullOrWhiteSpace(bookingId))
        {
            throw new ArgumentException("予約番号は必須です。", nameof(bookingId));
        }
        ArgumentNullException.ThrowIfNull(route);
        return new SelectedRoute(bookingId, route, RouteStatus.Confirmed, 0);
    }

    /// <summary>永続化データから再構築する。</summary>
    public static SelectedRoute Reconstruct(string bookingId, CandidateRoute route, RouteStatus status, long version)
        => new(bookingId, route, status, version);
}
