using CargoTracker.Billing.Domain.Model;

namespace CargoTracker.Billing.Domain.Repositories;

/// <summary>精算書リポジトリ（US21-23）。</summary>
public interface IInvoiceRepository
{
    Task SaveAsync(Invoice invoice, CancellationToken ct = default);

    Task<Invoice?> FindByBookingIdAsync(string bookingId, CancellationToken ct = default);

    Task<Invoice?> FindByInvoiceNumberAsync(string invoiceNumber, CancellationToken ct = default);

    Task<bool> ExistsForBookingAsync(string bookingId, CancellationToken ct = default);
}
