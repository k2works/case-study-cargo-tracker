namespace CargoTracker.Tracking.Domain.Model;

/// <summary>通知先種別（US19/US20）。荷主または管理職。</summary>
public enum NotificationRecipient
{
    Shipper,
    Management,
}

/// <summary>
/// 例外通知記録（US19/US20・append-only）。例外検知に応じて荷主・管理職へ通知した事実を記録する。
/// メール実送信基盤は後続 IT のため、本 IT では記録で代替する。
/// </summary>
public sealed class ExceptionNotification
{
    public string TrackingNumber { get; }
    public string BookingId { get; }
    public NotificationRecipient Recipient { get; }
    public string ExceptionType { get; }
    public string Message { get; }
    public DateTimeOffset NotifiedAt { get; }

    public ExceptionNotification(
        string trackingNumber, string bookingId, NotificationRecipient recipient,
        string exceptionType, string message, DateTimeOffset notifiedAt)
    {
        TrackingNumber = trackingNumber;
        BookingId = bookingId;
        Recipient = recipient;
        ExceptionType = exceptionType;
        Message = message;
        NotifiedAt = notifiedAt;
    }

    /// <summary>荷主向け通知記録を生成する。</summary>
    public static ExceptionNotification ForShipper(
        string trackingNumber, string bookingId, string exceptionType, DateTimeOffset notifiedAt)
        => new(trackingNumber, bookingId, NotificationRecipient.Shipper, exceptionType,
            $"貨物 {trackingNumber} に例外（{exceptionType}）が発生しました。詳細は追跡照会をご確認ください。", notifiedAt);

    /// <summary>管理職向けエスカレーション通知記録を生成する（紛失＝Lost 等）。</summary>
    public static ExceptionNotification ForManagement(
        string trackingNumber, string bookingId, string exceptionType, DateTimeOffset notifiedAt)
        => new(trackingNumber, bookingId, NotificationRecipient.Management, exceptionType,
            $"【エスカレーション】貨物 {trackingNumber} に重大例外（{exceptionType}）が発生しました。至急対応が必要です。", notifiedAt);

    /// <summary>荷主向けの対応報告通知記録を生成する（US19 AC4/US20 AC5・例外解決時）。</summary>
    public static ExceptionNotification ForResolution(
        string trackingNumber, string bookingId, string exceptionType,
        string? resolutionNotes, DateTimeOffset notifiedAt)
        => new(trackingNumber, bookingId, NotificationRecipient.Shipper, exceptionType,
            $"貨物 {trackingNumber} の例外（{exceptionType}）に対応しました。{(string.IsNullOrWhiteSpace(resolutionNotes) ? string.Empty : $"対応内容: {resolutionNotes}")}", notifiedAt);
}
