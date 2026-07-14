using CargoTracker.Billing.Domain.Repositories;
using CargoTracker.Shared.Application.Persistence;

namespace CargoTracker.Billing.Application.Internal.CommandServices;

/// <summary>
/// 支払期限を超過した未払い精算書を延滞（Overdue）へ遷移させるユースケース（US23 AC5）。
/// 精算書照会時に呼び出し、期限超過を検知して状態を更新する（バッチ/スケジューラ導入までの暫定起動点）。
/// </summary>
public sealed class MarkOverdueInvoicesCommandService(
    IUnitOfWorkFactory unitOfWorkFactory,
    IInvoiceRepository invoiceRepository)
{
    /// <summary>指定精算書が期限超過の未払いなら延滞へ遷移する。変化がなければ何もしない。</summary>
    public async Task MarkIfOverdueAsync(string invoiceNumber, DateTimeOffset asOf, CancellationToken ct = default)
    {
        var invoice = await invoiceRepository.FindByInvoiceNumberAsync(invoiceNumber, ct);
        if (invoice is null)
        {
            return;
        }

        var before = invoice.PaymentStatus;
        invoice.MarkOverdue(asOf);
        if (invoice.PaymentStatus == before)
        {
            return; // 期限内、または既に確定/延滞済み。
        }

        await using var unitOfWork = unitOfWorkFactory.Begin();
        unitOfWork.Track(invoice);
        await invoiceRepository.SaveAsync(invoice, ct);
        await unitOfWork.CommitAsync(ct);
    }

    /// <summary>未払い精算書一覧を走査し、期限超過分をまとめて延滞へ遷移する（一覧照会時の起動用）。</summary>
    public async Task MarkAllOverdueAsync(IEnumerable<string> pendingInvoiceNumbers, DateTimeOffset asOf, CancellationToken ct = default)
    {
        foreach (var invoiceNumber in pendingInvoiceNumbers)
        {
            await MarkIfOverdueAsync(invoiceNumber, asOf, ct);
        }
    }
}
