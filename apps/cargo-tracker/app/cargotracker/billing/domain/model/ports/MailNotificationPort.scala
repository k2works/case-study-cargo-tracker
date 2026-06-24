package cargotracker.billing.domain.model.ports

/** Billing Context から荷主向けにメール通知を送信するための出力ポート (IT8 US23 タスク 2.9 / ADR 0018)。
  *
  *   - 実装 (Adapter) は infrastructure 配下に配置。IT8 では `LoggingMailNotificationAdapter` (ログ出力のみ) を提供し、 IT9 以降で Pekko Mail /
  *     AWS SES 等を連携する想定 (ADR 0018 参照)
  *   - 送信失敗は Either で表現するが、IT8 では業務的に致命的でないためベストエフォート扱い (`BillingCommandService.issuePayment` / `confirmPayment` /
  *     `detectOverdue` で失敗時もメインフローは続行)
  */
trait MailNotificationPort:
  /** 支払発行通知メール: 荷主に支払期日と入金参照コードを伝える。 */
  def sendPaymentRequested(
      bookingId: String,
      invoiceNumber: String,
      dueDate: String,
      paymentReference: String,
      amount: Long
  ): Either[String, Unit]

  /** 入金確認通知メール: 荷主に入金確認の事実を伝える。 */
  def sendPaymentConfirmed(
      bookingId: String,
      invoiceNumber: String,
      paidAt: String,
      amount: Long
  ): Either[String, Unit]

  /** 期限超過通知メール: 荷主に支払期日超過の警告を伝える。 */
  def sendOverdueAlert(
      bookingId: String,
      invoiceNumber: String,
      dueDate: String,
      amount: Long
  ): Either[String, Unit]
