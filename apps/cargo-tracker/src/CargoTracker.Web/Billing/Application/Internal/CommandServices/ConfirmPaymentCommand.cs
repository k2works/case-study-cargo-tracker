namespace CargoTracker.Billing.Application.Internal.CommandServices;

/// <summary>入金を確認するコマンド（US23）。経理担当者が精算書の入金を確認し精算済へ進める。</summary>
public sealed record ConfirmPaymentCommand(
    string InvoiceNumber,
    string PaymentMethod);
