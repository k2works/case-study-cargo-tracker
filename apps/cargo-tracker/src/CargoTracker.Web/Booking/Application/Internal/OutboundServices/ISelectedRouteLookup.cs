namespace CargoTracker.Booking.Application.Internal.OutboundServices;

/// <summary>確定経路の区間（Booking 側 DTO）。Routing の selected_route を Booking プリミティブで受け取る。</summary>
public sealed class SelectedRouteLegDto
{
    public string VoyageNumber { get; set; } = string.Empty;
    public string LoadUnLocode { get; set; } = string.Empty;
    public string UnloadUnLocode { get; set; } = string.Empty;
    public DateTimeOffset LoadTime { get; set; }
    public DateTimeOffset UnloadTime { get; set; }
}

/// <summary>
/// Routing の確定経路（US09）を Booking から参照する ACL（US11）。
/// Routing の内部モデルに依存せず、確定経路の区間を Booking 側プリミティブで取得する
/// （ShipperExistenceChecker と同じ ACL パターン。BC 独立）。
/// </summary>
public interface ISelectedRouteLookup
{
    Task<IReadOnlyList<SelectedRouteLegDto>> FindLegsByBookingIdAsync(string bookingId, CancellationToken ct = default);
}
