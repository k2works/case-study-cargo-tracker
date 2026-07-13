namespace CargoTracker.Handling.Application.Internal.OutboundServices;

/// <summary>追跡番号から予約番号を解決する ACL（US15）。Tracking Context の tracking_activity を読取参照する。</summary>
public interface ITrackingNumberResolver
{
    Task<string?> ResolveBookingIdAsync(string trackingNumber, CancellationToken ct = default);
}
