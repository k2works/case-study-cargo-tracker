package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.repositories.{BillingCargoQueryPort, InvoiceRepository}
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, BillingShipperId, DiscountRate}
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
            base <- pricingService
              .calculateActual(
                snapshot.origin,
                snapshot.destination,
                snapshot.cargoType,
                snapshot.weight,
                snapshot.voyageNumbers
              )
              .left
              .map(_ => "料金算出に失敗しました")
            shipper = BillingShipperId(snapshot.shipperId, command.isCorporate)
            invoice <- Invoice
              .issue(
                invoiceRepository.nextInvoiceId(),
                snapshot.bookingId,
                shipper,
                base,
                command.discountRate.getOrElse(DiscountRate.zero),
                clock.instant()
              )
              .left
              .map(_ => "請求書の発行に失敗しました")
          yield
            invoiceRepository.save(invoice)
            invoice
    yield result

/** 請求書発行コマンド（US21）。 */
final case class GenerateInvoiceCommand(
    bookingId: String,
    isCorporate: Boolean = false,
    discountRate: Option[DiscountRate] = None
)
