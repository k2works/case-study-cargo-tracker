using System.Data;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Infrastructure.Persistence;
using CargoTracker.Tracking.Domain.Model;
using CargoTracker.Tracking.Domain.Repositories;
using Dapper;

namespace CargoTracker.Tracking.Infrastructure.Repositories;

/// <summary>Dapper による例外通知記録リポジトリ（US19/US20・append-only）。</summary>
public sealed class ExceptionNotificationRepository(IDbConnectionFactory connectionFactory, AmbientTransaction ambient)
    : IExceptionNotificationRepository
{
    public async Task SaveAsync(ExceptionNotification notification, CancellationToken ct = default)
    {
        var now = ToDatabaseTimestamp(DateTimeOffset.UtcNow);
        var tx = ambient.Require();
        await tx.Connection!.ExecuteAsync(new CommandDefinition(
            """
            INSERT INTO exception_notification
                (tracking_number, booking_id, recipient_type, exception_type, message, notified_at, created_at, updated_at)
            VALUES
                (@TrackingNumber, @BookingId, @RecipientType, @ExceptionType, @Message, @NotifiedAt, @Now, @Now)
            """,
            new
            {
                notification.TrackingNumber,
                notification.BookingId,
                RecipientType = notification.Recipient.ToString().ToUpperInvariant(),
                notification.ExceptionType,
                notification.Message,
                NotifiedAt = ToDatabaseTimestamp(notification.NotifiedAt),
                Now = now,
            },
            tx, cancellationToken: ct));
    }

    public async Task<IReadOnlyList<ExceptionNotification>> FindByTrackingNumberAsync(
        string trackingNumber, CancellationToken ct = default)
    {
        if (ambient.Current is not null)
        {
            return await QueryAsync(trackingNumber, ambient.Current.Connection!, ambient.Current, ct);
        }

        using var connection = connectionFactory.Create();
        return await QueryAsync(trackingNumber, connection, null, ct);
    }

    private static async Task<IReadOnlyList<ExceptionNotification>> QueryAsync(
        string trackingNumber, IDbConnection connection, IDbTransaction? tx, CancellationToken ct)
    {
        var rows = await connection.QueryAsync<Row>(new CommandDefinition(
            """
            SELECT tracking_number AS TrackingNumber, booking_id AS BookingId,
                   recipient_type AS RecipientType, exception_type AS ExceptionType,
                   message AS Message, notified_at AS NotifiedAt
            FROM exception_notification
            WHERE tracking_number = @TrackingNumber
            ORDER BY id
            """,
            new { TrackingNumber = trackingNumber }, tx, cancellationToken: ct));
        return rows.Select(r => r.ToNotification()).ToList();
    }

    private static DateTime ToDatabaseTimestamp(DateTimeOffset value)
        => DatabaseTimestamp.ToDatabase(value);

    private sealed class Row
    {
        public string TrackingNumber { get; set; } = string.Empty;
        public string BookingId { get; set; } = string.Empty;
        public string RecipientType { get; set; } = string.Empty;
        public string ExceptionType { get; set; } = string.Empty;
        public string Message { get; set; } = string.Empty;
        public DateTime NotifiedAt { get; set; }

        public ExceptionNotification ToNotification() => new(
            TrackingNumber, BookingId,
            Enum.Parse<NotificationRecipient>(RecipientType, ignoreCase: true),
            ExceptionType, Message,
            new DateTimeOffset(DateTime.SpecifyKind(NotifiedAt, DateTimeKind.Utc)));
    }
}
