using CargoTracker.Billing.Application.Internal.CommandServices;
using CargoTracker.Billing.Application.Internal.QueryServices;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Billing.Interfaces;

/// <summary>精算書の一覧・詳細・発行・入金確認（US21/US22/US23）。経理担当者（ROLE_BILLING）が操作する。</summary>
[Authorize(Roles = Roles.Billing)]
public sealed class BillingController(
    InvoiceQueryService queryService,
    GenerateInvoiceCommandService generateInvoiceCommandService,
    ConfirmPaymentCommandService confirmPaymentCommandService,
    MarkOverdueInvoicesCommandService markOverdueInvoicesCommandService) : Controller
{
    [HttpGet("/billing/invoices")]
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        // US23 AC5: 照会時に期限超過の未払いを延滞（Overdue）へ遷移させる（バッチ導入までの暫定起動点）。
        var current = await queryService.ListAsync(ct);
        var pending = current.Where(i => i.PaymentStatus == "PENDING").Select(i => i.InvoiceNumber);
        await markOverdueInvoicesCommandService.MarkAllOverdueAsync(pending, DateTimeOffset.UtcNow, ct);

        var invoices = await queryService.ListAsync(ct);
        return View("Index", invoices);
    }

    [HttpGet("/billing/invoices/{invoiceNumber}")]
    public async Task<IActionResult> Detail(string invoiceNumber, CancellationToken ct)
    {
        await markOverdueInvoicesCommandService.MarkIfOverdueAsync(invoiceNumber, DateTimeOffset.UtcNow, ct);
        var detail = await queryService.FindByInvoiceNumberAsync(invoiceNumber, ct);
        return View("Detail", detail);
    }

    [HttpPost("/billing/invoices")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Generate(string bookingId, CancellationToken ct)
    {
        try
        {
            var invoiceNumber = await generateInvoiceCommandService.HandleAsync(
                new GenerateInvoiceCommand(bookingId, DateTimeOffset.UtcNow), ct);
            TempData["SuccessMessage"] = "精算書を発行しました。";
            return LocalRedirect($"/billing/invoices/{invoiceNumber}");
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            TempData["WarningMessage"] = ex.Message;
            return LocalRedirect("/billing/invoices");
        }
    }

    [HttpPost("/billing/invoices/{invoiceNumber}/payment")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> ConfirmPayment(string invoiceNumber, string? paymentMethod, CancellationToken ct)
    {
        try
        {
            await confirmPaymentCommandService.HandleAsync(
                new ConfirmPaymentCommand(invoiceNumber, string.IsNullOrWhiteSpace(paymentMethod) ? "銀行振込" : paymentMethod), ct);
            TempData["SuccessMessage"] = "入金を確認し精算済にしました。";
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/billing/invoices/{invoiceNumber}");
    }
}
