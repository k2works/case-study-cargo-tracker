using System.Data;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Persistence;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;
using Dapper;

namespace CargoTracker.Tracking.Infrastructure.Repositories;

/// <summary>Dapper による追跡レコードリポジトリ（US14）。追跡番号発行時にヘッダを作成し、イベントを seq 連鎖で保存する。</summary>
public sealed class TrackingActivityRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient)
    : ITrackingActivityRepository
{
    public async Task SaveAsync(TrackingActivity trackingActivity, CancellationToken ct = default)
    {
        var now = ToDatabaseTimestamp(DateTimeOffset.UtcNow);
        var tx = ambient.Require();
        var connection = tx.Connection!;

        var existingId = await connection.ExecuteScalarAsync<long?>(new CommandDefinition(
            "SELECT id FROM tracking_activity WHERE booking_id = @BookingId",
            new { BookingId = trackingActivity.BookingId.Value }, tx, cancellationToken: ct));

        long trackingId;
        if (existingId is null)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO tracking_activity
                    (tracking_number, booking_id, transport_status, created_at, updated_at, version)
                VALUES
                    (@TrackingNumber, @BookingId, @TransportStatus, @Now, @Now, @Version)
                """,
                new
                {
                    TrackingNumber = trackingActivity.TrackingNumber.Value,
                    BookingId = trackingActivity.BookingId.Value,
                    TransportStatus = ToDbStatus(trackingActivity.CurrentStatus()),
                    Now = now,
                    trackingActivity.Version,
                },
                tx, cancellationToken: ct));
            trackingId = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
                "SELECT id FROM tracking_activity WHERE booking_id = @BookingId",
                new { BookingId = trackingActivity.BookingId.Value }, tx, cancellationToken: ct));
        }
        else
        {
            trackingId = existingId.Value;
            await connection.ExecuteAsync(new CommandDefinition(
                "UPDATE tracking_activity SET transport_status = @TransportStatus, version = @Version, updated_at = @Now WHERE id = @Id",
                new
                {
                    TransportStatus = ToDbStatus(trackingActivity.CurrentStatus()),
                    trackingActivity.Version,
                    Now = now,
                    Id = trackingId,
                },
                tx, cancellationToken: ct));
        }

        await connection.ExecuteAsync(new CommandDefinition(
            "DELETE FROM tracking_handling_event WHERE tracking_id = @Id", new { Id = trackingId }, tx, cancellationToken: ct));

        var seq = 1;
        foreach (var activityEvent in trackingActivity.Events)
        {
            await connection.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO tracking_handling_event
                    (tracking_id, seq_number, event_type, event_time, location_unlocode, voyage_number, created_at, updated_at)
                VALUES
                    (@TrackingId, @Seq, @EventType, @EventTime, @Location, @VoyageNumber, @Now, @Now)
                """,
                new
                {
                    TrackingId = trackingId,
                    Seq = seq,
                    EventType = activityEvent.EventType.ToString().ToUpperInvariant(),
                    EventTime = ToDatabaseTimestamp(activityEvent.CompletionTime),
                    Location = activityEvent.Location.UnLocode,
                    VoyageNumber = activityEvent.VoyageNumber?.Number,
                    Now = now,
                },
                tx, cancellationToken: ct));
            seq++;
        }
    }

    public Task<TrackingActivity?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default)
        => QueryAsync("booking_id", bookingId, ct);

    public Task<TrackingActivity?> FindByTrackingNumberAsync(string trackingNumber, CancellationToken ct = default)
        => QueryAsync("tracking_number", trackingNumber, ct);

    public async Task<bool> ExistsForBookingAsync(string bookingId, CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var count = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT COUNT(*) FROM tracking_activity WHERE booking_id = @BookingId",
            new { BookingId = bookingId }, cancellationToken: ct));
        return count > 0;
    }

    private async Task<TrackingActivity?> QueryAsync(string column, string value, CancellationToken ct)
    {
        if (ambient.Current is not null)
        {
            return await LoadAsync(column, value, ambient.Current.Connection!, ambient.Current, ct);
        }
        using var connection = connectionFactory.Create();
        return await LoadAsync(column, value, connection, null, ct);
    }

    private static async Task<TrackingActivity?> LoadAsync(
        string column, string value, IDbConnection connection, IDbTransaction? tx, CancellationToken ct)
    {
        var header = await connection.QuerySingleOrDefaultAsync<HeaderRow>(new CommandDefinition(
            $"SELECT id AS Id, tracking_number AS TrackingNumber, booking_id AS BookingId, version AS Version FROM tracking_activity WHERE {column} = @Value",
            new { Value = value }, tx, cancellationToken: ct));
        if (header is null)
        {
            return null;
        }

        var eventRows = await connection.QueryAsync<EventRow>(new CommandDefinition(
            """
            SELECT event_type AS EventType, event_time AS EventTime,
                   location_unlocode AS LocationUnlocode, voyage_number AS VoyageNumber
            FROM tracking_handling_event WHERE tracking_id = @Id ORDER BY seq_number
            """,
            new { header.Id }, tx, cancellationToken: ct));

        var events = eventRows.Select(r => r.ToEvent()).ToList();
        return TrackingActivity.Reconstruct(
            new TrackingNumber(header.TrackingNumber), new TrackingBookingId(header.BookingId), events, header.Version);
    }

    private static DateTime ToDatabaseTimestamp(DateTimeOffset value)
        => DateTime.SpecifyKind(value.UtcDateTime, DateTimeKind.Unspecified);

    private static string ToDbStatus(TrackingStatus status)
    {
        var name = status.ToString();
        var builder = new System.Text.StringBuilder(name.Length + 4);
        for (var i = 0; i < name.Length; i++)
        {
            if (i > 0 && char.IsUpper(name[i]))
            {
                builder.Append('_');
            }
            builder.Append(char.ToUpperInvariant(name[i]));
        }
        return builder.ToString();
    }

    private sealed class HeaderRow
    {
        public long Id { get; set; }
        public string TrackingNumber { get; set; } = string.Empty;
        public string BookingId { get; set; } = string.Empty;
        public long Version { get; set; }
    }

    private sealed class EventRow
    {
        public string EventType { get; set; } = string.Empty;
        public DateTime EventTime { get; set; }
        public string LocationUnlocode { get; set; } = string.Empty;
        public string? VoyageNumber { get; set; }

        public TrackingActivityEvent ToEvent() => new(
            Enum.Parse<TrackingEventType>(EventType, ignoreCase: true),
            new TrackingLocation(LocationUnlocode),
            new DateTimeOffset(DateTime.SpecifyKind(EventTime, DateTimeKind.Utc)),
            string.IsNullOrWhiteSpace(VoyageNumber) ? null : new TrackingVoyageNumber(VoyageNumber));
    }
}
