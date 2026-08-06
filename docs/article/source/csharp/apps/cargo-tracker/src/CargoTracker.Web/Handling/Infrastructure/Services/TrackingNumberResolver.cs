using CargoTracker.Handling.Application.Internal.OutboundServices;
using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Handling.Infrastructure.Services;

/// <summary>追跡番号→予約番号の解決 ACL 実装（US15）。tracking_activity を SQL 直接参照する。</summary>
public sealed class TrackingNumberResolver(IDbConnectionFactory connectionFactory) : ITrackingNumberResolver
{
    public async Task<string?> ResolveBookingIdAsync(string trackingNumber, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        return await connection.ExecuteScalarAsync<string?>(new CommandDefinition(
            "SELECT booking_id FROM tracking_activity WHERE tracking_number = @TrackingNumber",
            new { TrackingNumber = trackingNumber.Trim().ToUpperInvariant() }, cancellationToken: ct));
    }
}
