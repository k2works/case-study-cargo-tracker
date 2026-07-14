using CargoTracker.Billing.Application.Internal.OutboundServices;

namespace CargoTracker.Billing.Infrastructure.Services;

/// <summary>
/// 決済機関ポートのスタブ実装（US23・本 IT）。入金を即時確認したものとして返す。
/// 実決済機関連携時はアダプターを差し替える。
/// </summary>
public sealed class StubPaymentGateway : IPaymentGatewayPort
{
    public Task<PaymentConfirmation> ConfirmPaymentAsync(
        string invoiceNumber, string paymentMethod, DateTimeOffset asOf, CancellationToken ct = default)
        => Task.FromResult(new PaymentConfirmation(true, asOf, $"STUB-{invoiceNumber}"));
}
