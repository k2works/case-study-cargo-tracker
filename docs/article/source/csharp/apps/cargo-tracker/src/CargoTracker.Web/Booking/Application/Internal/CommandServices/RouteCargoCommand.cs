namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>
/// 確定経路を予約に紐付けるコマンド（US11）。
/// 旅程区間は Booking 側プリミティブで受け取り、Routing の CandidateRoute への型依存を持たない
/// （BC 独立。Routing→Booking の変換は境界（UI/ACL）で行う）。
/// </summary>
public sealed record RouteCargoCommand(string BookingId, IReadOnlyList<RouteLegInput> Legs);

/// <summary>旅程区間の入力（US11）。時刻は UTC 基準の DateTimeOffset で受け取る。</summary>
public sealed record RouteLegInput(
    string VoyageNumber,
    string LoadUnLocode,
    string UnloadUnLocode,
    DateTimeOffset LoadTime,
    DateTimeOffset UnloadTime);
