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
    GenerateInvoiceCommandService generateInvoiceCommandService) : Controller
{
    [HttpGet("/billing/invoices")]
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var invoices = await queryService.ListAsync(ct);
        return View("Index", invoices);
    }

    [HttpGet("/billing/invoices/{invoiceNumber}")]
    public async Task<IActionResult> Detail(string invoiceNumber, CancellationToken ct)
    {
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
}
