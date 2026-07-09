using System.Data;
using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Domain.Repositories;

/// <summary>貨物予約の永続化ポート。</summary>
public interface ICargoRepository
{
    Task SaveAsync(Cargo cargo, IDbTransaction transaction, CancellationToken ct = default);

    Task UpdateAsync(Cargo cargo, IDbTransaction transaction, CancellationToken ct = default);

    Task<Cargo?> FindByBookingIdAsync(BookingId id, CancellationToken ct = default);

    Task<Cargo?> FindByBookingIdAsync(BookingId id, IDbTransaction transaction, CancellationToken ct = default);
}
