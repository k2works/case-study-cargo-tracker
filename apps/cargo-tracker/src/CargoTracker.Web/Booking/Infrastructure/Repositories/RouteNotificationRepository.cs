using System.Data;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Booking.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Persistence;
using Dapper;

namespace CargoTracker.Booking.Infrastructure.Repositories;

/// <summary>Dapper による経路通知記録リポジトリ（US12）。</summary>
public sealed class RouteNotificationRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient)
    : IRouteNotificationRepository
{
    public async Task SaveAsync(RouteNotification notification, CancellationToken ct = default)
    {
        var now = ToDatabaseTimestamp(DateTimeOffset.UtcNow);
        var tx = ambient.Require();
        await tx.Connection!.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO route_notification
                (booking_id, notified_at, expected_arrival_time, created_at, updated_at)
            VALUES
                (@BookingId, @NotifiedAt, @ExpectedArrivalTime, @Now, @Now)
            """,
            new
            {
                BookingId = notification.BookingId.Value,
                NotifiedAt = ToDatabaseTimestamp(notification.NotifiedAt),
                ExpectedArrivalTime = ToDatabaseTimestamp(notification.ExpectedArrivalTime),
                Now = now,
            },
            tx, cancellationToken: ct));
    }

    public async Task<RouteNotification?> FindLatestByBookingIdAsync(BookingId bookingId, CancellationToken ct = default)
    {
        if (ambient.Current is not null)
        {
            return await QueryAsync(bookingId, ambient.Current.Connection!, ambient.Current, ct);
        }

        using var connection = connectionFactory.Create();
        return await QueryAsync(bookingId, connection, null, ct);
    }

    private static async Task<RouteNotification?> QueryAsync(
        BookingId bookingId, IDbConnection connection, IDbTransaction? tx, CancellationToken ct)
    {
        var row = await connection.QuerySingleOrDefaultAsync<Row>(new CommandDefinition(
            """
            SELECT booking_id AS BookingId, notified_at AS NotifiedAt,
                   expected_arrival_time AS ExpectedArrivalTime
            FROM route_notification
            WHERE booking_id = @BookingId
            ORDER BY notified_at DESC, id DESC
            LIMIT 1
            """,
            new { BookingId = bookingId.Value }, tx, cancellationToken: ct));
        return row?.ToNotification();
    }

    private static DateTime ToDatabaseTimestamp(DateTimeOffset value)
        => DatabaseTimestamp.ToDatabase(value);

    private sealed class Row
    {
        public string BookingId { get; set; } = string.Empty;
        public DateTime NotifiedAt { get; set; }
        public DateTime ExpectedArrivalTime { get; set; }

        public RouteNotification ToNotification() => RouteNotification.Reconstruct(
            new BookingId(BookingId),
            new DateTimeOffset(DateTime.SpecifyKind(NotifiedAt, DateTimeKind.Utc)),
            new DateTimeOffset(DateTime.SpecifyKind(ExpectedArrivalTime, DateTimeKind.Utc)));
    }
}
