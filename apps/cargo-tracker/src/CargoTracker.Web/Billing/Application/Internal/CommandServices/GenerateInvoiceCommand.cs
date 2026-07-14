namespace CargoTracker.Billing.Application.Internal.CommandServices;

/// <summary>精算書を発行するコマンド（US21/US22）。経理担当者が配送完了予約の料金を算出し割引を適用する。</summary>
public sealed record GenerateInvoiceCommand(
    string BookingId,
    DateTimeOffset IssuedAt,
    int PaymentTermDays = 30);
