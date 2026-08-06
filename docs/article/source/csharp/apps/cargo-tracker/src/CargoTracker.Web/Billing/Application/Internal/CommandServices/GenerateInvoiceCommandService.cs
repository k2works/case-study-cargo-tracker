using CargoTracker.Billing.Application.Internal.OutboundServices;
using CargoTracker.Billing.Domain.Model;
using CargoTracker.Billing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Billing.Application.Internal.CommandServices;

/// <summary>
/// 精算書を発行するユースケース（US21/US22）。配送完了（Delivered）予約の輸送基本料金を算出し、
/// 法人割引を適用して最終金額を確定、精算書を発行する。
/// 請求書発行は Delivered 状態の予約に限定する（改善 #16）。
/// </summary>
public sealed class GenerateInvoiceCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IInvoiceRepository invoiceRepository,
    IBillingSnapshotProvider snapshotProvider)
{
    public async Task<string> HandleAsync(GenerateInvoiceCommand command, CancellationToken ct = default)
    {
        var snapshot = await snapshotProvider.FindByBookingIdAsync(command.BookingId, ct)
            ?? throw new InvalidOperationException("指定された予約が見つかりません。");

        // 改善 #16: 配送完了（Delivered）の予約のみ精算書を発行できる。
        if (!string.Equals(snapshot.BookingStatus, "DELIVERED", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException("配送完了（Delivered）の予約のみ精算書を発行できます。");
        }

        if (await invoiceRepository.ExistsForBookingAsync(command.BookingId, ct))
        {
            throw new InvalidOperationException("この予約の精算書は既に発行済みです。");
        }

        var baseAmount = FreightCalculator.CalculateBaseAmount(snapshot.Weight, snapshot.CargoType);
        var shipperId = new BillingShipperId(snapshot.ShipperId, snapshot.ShipperType);
        var dueDate = command.IssuedAt.AddDays(command.PaymentTermDays);

        var invoice = Invoice.Issue(command.BookingId, shipperId, baseAmount, command.IssuedAt, dueDate);
        // US22: 法人荷主なら契約割引率を適用（個人荷主は Invoice 側で割引 0 に落ちる）。
        invoice.ApplyDiscount(new DiscountRate(snapshot.DiscountRate));

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(invoice);
        await invoiceRepository.SaveAsync(invoice, ct);
        await unitOfWork.CommitAsync(ct);

        return invoice.InvoiceId.Value;
    }
}
