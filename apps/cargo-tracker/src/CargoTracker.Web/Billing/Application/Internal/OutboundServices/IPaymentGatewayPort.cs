namespace CargoTracker.Billing.Application.Internal.OutboundServices;

/// <summary>入金確認結果（US23・決済機関 ACL）。</summary>
public sealed record PaymentConfirmation(bool Confirmed, DateTimeOffset PaidAt, string? TransactionReference);

/// <summary>
/// 決済機関との連携ポート（US23・ACL）。本 IT はスタブ（即時確認）。
/// 将来は実決済機関のアダプターに差し替える。
/// </summary>
public interface IPaymentGatewayPort
{
    Task<PaymentConfirmation> ConfirmPaymentAsync(
        string invoiceNumber, string paymentMethod, DateTimeOffset asOf, CancellationToken ct = default);
}
