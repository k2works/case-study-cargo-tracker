package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  InvoiceId,
  InvoiceLineItem,
  LineItemCategory
}
import cargotracker.booking.application.api.BookingPublicApi
import cargotracker.shared.application.OptimisticLockOps.withOptimisticLock
import cargotracker.shared.domain.pricing.PricingService

import java.time.{Clock, Instant, LocalDate}
import javax.inject.{Inject, Singleton}

/** 請求書発行コマンドサービス（US21 / IT6, IT7 0.2 で ACL 化）。
  *
  *   - `BookingStatus.Delivered` の予約のみ請求可（ACL の `isDelivered` で判定）
  *   - 既に請求済の予約は冪等成功（既存 Invoice を返す）
  *   - PricingService で実費 (`calculateActual`) を算出し `Invoice` を `Pending` で発行
  *   - Cargo は `BillingCargoQueryPort` 経由でスナップショットを取得し、Booking 集約への直接依存を回避
  */
@Singleton
class BillingCommandService @Inject() (
    invoiceRepository: InvoiceRepository,
    cargoQueryPort: BillingCargoQueryPort,
    pricingService: PricingService,
    bookingPublicApi: BookingPublicApi,
    clock: Clock
):

  def generate(command: GenerateInvoiceCommand): Either[String, Invoice] =
    val bid = BillingBookingId.unsafeFrom(command.bookingId)
    for
      snapshot <- cargoQueryPort
        .findForBilling(bid)
        .toRight(s"予約 ${command.bookingId} が見つかりません")
      _ <- Either.cond(
        snapshot.isDelivered,
        (),
        "予約は Delivered 状態である必要があります"
      )
      existing = invoiceRepository.findByBookingId(snapshot.bookingId)
      result <- existing match
        case Some(inv) => Right(inv)
        case None =>
          for
            breakdown <- pricingService
              .calculateActualWithBreakdown(
                snapshot.origin,
                snapshot.destination,
                snapshot.cargoType,
                snapshot.weight,
                snapshot.voyageNumbers
              )
              .left
              .map(_ => "料金算出に失敗しました")
            shipper = BillingShipperId(snapshot.shipperId, snapshot.isCorporate)
            // IT8 US22: snapshot の corporateDiscountRate を優先採用（UI 入力 command.discountRate は fallback、互換維持）
            effectiveDiscountRate <- snapshot.corporateDiscountRate
              .map(rate => DiscountRate(rate).left.map(_ => s"法人割引率が範囲外です: $rate"))
              .getOrElse(Right(command.discountRate.getOrElse(DiscountRate.zero)))
            baseItems = BillingCommandService.toInvoiceLineItems(breakdown.items)
            allItems = BillingCommandService.appendDiscountLineItem(baseItems, breakdown.total, effectiveDiscountRate)
            invoice <- Invoice
              .issue(
                invoiceRepository.nextInvoiceId(),
                snapshot.bookingId,
                shipper,
                breakdown.total,
                effectiveDiscountRate,
                clock.instant(),
                allItems
              )
              .left
              .map(_ => "請求書の発行に失敗しました")
          yield
            invoiceRepository.save(invoice)
            invoice
    yield result

  /** IT8 US23 (ADR 0019 案 B): NotIssued の Invoice に支払期日と入金参照コードを設定し Pending に遷移する。
    *
    *   - 確定済 Invoice (Pending / Confirmed) からの再発行はドメイン側で拒否される
    *   - 成功時は PaymentRequested 通知を Booking Context に記録 (荷主向けメール送信は MailNotificationPort 経由、IT8 2.9 で実装)
    */
  def issuePayment(command: IssuePaymentCommand): Either[String, Invoice] =
    for
      invoice <- invoiceRepository
        .findById(InvoiceId.unsafeFrom(command.invoiceNumber))
        .toRight(s"請求書 ${command.invoiceNumber} が見つかりません")
      updated <- invoice.issuePayment(command.dueDate, command.referenceCode).left.map {
        case _: Invoice.InvalidPaymentStateTransition =>
          s"請求書 ${command.invoiceNumber} は支払発行可能な状態ではありません (現状態: ${invoice.paymentStatus})"
        case _ => "支払発行に失敗しました"
      }
      saved <- withOptimisticLock("請求書"):
        invoiceRepository.save(updated)
        updated
    yield
      bookingPublicApi.logPaymentRequested(
        bookingId = saved.cargoBookingId.value,
        invoiceNumber = saved.invoiceId.value,
        dueDate = command.dueDate.toString,
        paymentReference = command.referenceCode,
        amount = saved.finalAmount.amount
      )
      saved

  /** IT8 US23 (ADR 0019 案 B): Pending / Overdue Invoice に入金確認を反映し、Cargo を Settled に遷移する。
    *
    *   - Invoice の状態遷移: Pending|Overdue → Confirmed (paidAt 記録)
    *   - 連携: BookingPublicApi.markSettled で Cargo を Delivered → Settled
    *   - PaymentConfirmed 通知を Booking Context に記録
    *   - Cargo が Delivered 未到達 (markSettled が Left) の場合でも Invoice は Confirmed 状態で保存される (業務上、入金事実は記録優先)
    */
  def confirmPayment(command: ConfirmPaymentCommand): Either[String, Invoice] =
    for
      invoice <- invoiceRepository
        .findById(InvoiceId.unsafeFrom(command.invoiceNumber))
        .toRight(s"請求書 ${command.invoiceNumber} が見つかりません")
      updated <- invoice.confirmPayment(command.paidAt).left.map {
        case _: Invoice.InvalidPaymentStateTransition =>
          s"請求書 ${command.invoiceNumber} は入金確認可能な状態ではありません (現状態: ${invoice.paymentStatus})"
        case _ => "入金確認に失敗しました"
      }
      saved <- withOptimisticLock("請求書"):
        invoiceRepository.save(updated)
        updated
    yield
      // Cargo の Delivered → Settled 遷移 (失敗しても Invoice 保存は成功扱い、業務優先順)
      bookingPublicApi.markSettled(saved.cargoBookingId.value)
      bookingPublicApi.logPaymentConfirmed(
        bookingId = saved.cargoBookingId.value,
        invoiceNumber = saved.invoiceId.value,
        paidAt = command.paidAt.toString,
        amount = saved.finalAmount.amount
      )
      saved

  /** IT8 US23 (ADR 0019 案 B): 期限超過 Invoice を一括で Overdue 化する。
    *
    *   - 全 Pending Invoice を取得し、`Invoice.markOverdue(now)` が成功するものだけ更新する
    *   - 各遷移後に OverdueAlerted 通知を Booking Context に記録
    *   - 戻り値: Overdue 化された Invoice 数
    *   - IT8 では API のみ。Cron 実行は IT9 で Pekko Scheduler 連携予定
    *   - 個々の楽観ロック競合は無視 (次回バッチで再試行されるため)
    */
  def detectOverdue(now: LocalDate): Int =
    invoiceRepository.findAll().count { invoice =>
      invoice.markOverdue(now) match
        case Right(updated) =>
          try
            invoiceRepository.save(updated)
            bookingPublicApi.logOverdueAlerted(
              bookingId = updated.cargoBookingId.value,
              invoiceNumber = updated.invoiceId.value,
              dueDate = updated.dueDate.map(_.toString).getOrElse(""),
              amount = updated.finalAmount.amount
            )
            true
          catch case _: Throwable => false
        case Left(_) => false
    }

object BillingCommandService:
  /** PricingService の内訳明細を Billing の `InvoiceLineItem` に変換する（IT7 0.9）。 */
  def toInvoiceLineItems(items: List[PricingService.LineItem]): List[InvoiceLineItem] =
    items.map { i =>
      val cat = i.category match
        case PricingService.LineItemCategory.Distance => LineItemCategory.Distance
        case PricingService.LineItemCategory.Weight => LineItemCategory.Weight
        case PricingService.LineItemCategory.CargoType => LineItemCategory.CargoType
        case PricingService.LineItemCategory.Other => LineItemCategory.Other
      InvoiceLineItem(category = cat, name = i.name, amount = i.amount)
    }

  /** IT8 US22: 法人割引が 0% より大きい場合、Discount 明細行を追加する。`amount = -baseAmount × discountRate`。 */
  def appendDiscountLineItem(
      base: List[InvoiceLineItem],
      baseAmount: cargotracker.shared.domain.Money,
      discountRate: DiscountRate
  ): List[InvoiceLineItem] =
    if discountRate.value > BigDecimal(0) then
      val pct = (discountRate.percent.setScale(0, BigDecimal.RoundingMode.HALF_UP)).toBigInt
      val discountAmount = baseAmount.multiplyByRate(-discountRate.value)
      base :+ InvoiceLineItem(
        category = LineItemCategory.Discount,
        name = s"法人契約割引 ($pct%)",
        amount = discountAmount
      )
    else base

/** 請求書発行コマンド（US21、IT7 0.8 で `isCorporate` を廃止し Shipper 自動判定に統一）。 */
final case class GenerateInvoiceCommand(
    bookingId: String,
    discountRate: Option[DiscountRate] = None
)

/** 支払発行コマンド (US23 / IT8 ADR 0019 案 B)。 */
final case class IssuePaymentCommand(
    invoiceNumber: String,
    dueDate: LocalDate,
    referenceCode: String
)

/** 入金確認コマンド (US23 / IT8)。 */
final case class ConfirmPaymentCommand(invoiceNumber: String, paidAt: Instant)
