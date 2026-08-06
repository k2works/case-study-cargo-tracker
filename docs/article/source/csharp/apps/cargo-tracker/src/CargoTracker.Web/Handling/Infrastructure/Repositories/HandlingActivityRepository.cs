using CargoTracker.Handling.Domain.Model;
using CargoTracker.Handling.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Handling.Infrastructure.Repositories;

/// <summary>Dapper による荷役作業記録リポジトリ（US15）。追記型で保存する。</summary>
public sealed class HandlingActivityRepository(AmbientTransaction ambient) : IHandlingActivityRepository
{
    public async Task SaveAsync(HandlingActivity handlingActivity, CancellationToken ct = default)
    {
        var now = DateTime.SpecifyKind(DateTimeOffset.UtcNow.UtcDateTime, DateTimeKind.Unspecified);
        var tx = ambient.Require();
        await tx.Connection!.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO handling_activity
                (booking_id, event_type, event_completion_time, location_unlocode, voyage_number, created_at, updated_at)
            VALUES
                (@BookingId, @EventType, @CompletionTime, @Location, @VoyageNumber, @Now, @Now)
            """,
            new
            {
                BookingId = handlingActivity.BookingId.Value,
                EventType = handlingActivity.EventTypeName.ToUpperInvariant(),
                CompletionTime = DateTime.SpecifyKind(handlingActivity.CompletionTime.UtcDateTime, DateTimeKind.Unspecified),
                Location = handlingActivity.Location.UnLocode,
                VoyageNumber = handlingActivity.VoyageNumber?.Number,
                Now = now,
            },
            tx, cancellationToken: ct));
    }
}
