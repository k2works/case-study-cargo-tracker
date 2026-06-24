package cargotracker.billing.infrastructure.mail

import cargotracker.billing.domain.model.ports.MailNotificationPort
import play.api.Logger

import javax.inject.{Inject, Singleton}

/** IT8 US23 タスク 2.9 / ADR 0018: メール送信の暫定実装 (Pekko Mail / SES 等は IT9 で連携予定)。
  *
  *   - 実体は Play Logger による INFO 出力。実メール送信は行わない
  *   - 出力フォーマット: `[MAIL:<type>] bookingId=BK-X invoice=INV-X ...`
  *   - 戻り値は常に Right(())。失敗扱いは IT9 で実装側に追加
  */
@Singleton
class LoggingMailNotificationAdapter @Inject() () extends MailNotificationPort:
  private val logger: Logger = Logger("billing.mail")

  override def sendPaymentRequested(
      bookingId: String,
      invoiceNumber: String,
      dueDate: String,
      paymentReference: String,
      amount: Long
  ): Either[String, Unit] =
    logger.info(
      s"[MAIL:PaymentRequested] bookingId=$bookingId invoice=$invoiceNumber dueDate=$dueDate ref=$paymentReference amount=$amount"
    )
    Right(())

  override def sendPaymentConfirmed(
      bookingId: String,
      invoiceNumber: String,
      paidAt: String,
      amount: Long
  ): Either[String, Unit] =
    logger.info(
      s"[MAIL:PaymentConfirmed] bookingId=$bookingId invoice=$invoiceNumber paidAt=$paidAt amount=$amount"
    )
    Right(())

  override def sendOverdueAlert(
      bookingId: String,
      invoiceNumber: String,
      dueDate: String,
      amount: Long
  ): Either[String, Unit] =
    logger.info(
      s"[MAIL:OverdueAlert] bookingId=$bookingId invoice=$invoiceNumber dueDate=$dueDate amount=$amount"
    )
    Right(())
