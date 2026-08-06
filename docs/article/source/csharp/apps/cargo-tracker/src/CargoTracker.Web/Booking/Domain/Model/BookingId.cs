namespace CargoTracker.Booking.Domain.Model;

/// <summary>予約識別子（業務キー）。</summary>
public sealed record BookingId(string Value)
{
    public static BookingId Generate() => new($"BKG-{DateTimeOffset.UtcNow:yyyyMMdd}-{Guid.NewGuid():N}"[..20].ToUpperInvariant());
}
