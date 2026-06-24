package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  InvoiceLineItem,
  LineItemCategory
}
import cargotracker.shared.domain.pricing.PricingService

import java.time.Clock
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
