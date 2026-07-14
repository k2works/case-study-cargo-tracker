using MediatR;

namespace CargoTracker.Billing.Domain.Events;

/// <summary>
/// 入金が確認されたことを表すドメインイベント（US23）。
/// Booking Context が消費し、予約を精算済（Settled）へ同期する（ADR-0009 の結果整合性方針に従う）。
/// BC 独立のためプリミティブのみを運ぶ。
/// </summary>
public sealed record PaymentConfirmedEvent(
    string BookingId,
    string InvoiceNumber,
    DateTimeOffset PaidAt) : INotification;
