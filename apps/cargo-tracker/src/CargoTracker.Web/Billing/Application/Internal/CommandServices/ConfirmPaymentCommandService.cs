using CargoTracker.Billing.Application.Internal.OutboundServices;
using CargoTracker.Billing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Billing.Application.Internal.CommandServices;

/// <summary>
/// 入金を確認するユースケース（US23）。決済機関（スタブ）で入金を確認し、精算書を精算済（Confirmed）へ遷移する。
/// PaymentConfirmedEvent が post-commit で発行され、Booking 側が予約を精算済（Settled）へ同期する。
/// </summary>
public sealed class ConfirmPaymentCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IInvoiceRepository invoiceRepository,
    IPaymentGatewayPort paymentGateway)
{
    public async Task HandleAsync(ConfirmPaymentCommand command, CancellationToken ct = default)
    {
        var invoice = await invoiceRepository.FindByInvoiceNumberAsync(command.InvoiceNumber, ct)
            ?? throw new InvalidOperationException("指定された精算書が見つかりません。");

        var confirmation = await paymentGateway.ConfirmPaymentAsync(
            command.InvoiceNumber, command.PaymentMethod, DateTimeOffset.UtcNow, ct);
        if (!confirmation.Confirmed)
        {
            throw new InvalidOperationException("決済機関で入金を確認できませんでした。");
        }

        invoice.ConfirmPayment(confirmation.PaidAt);

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(invoice);
        await invoiceRepository.SaveAsync(invoice, ct);
        await unitOfWork.CommitAsync(ct);
    }
}
