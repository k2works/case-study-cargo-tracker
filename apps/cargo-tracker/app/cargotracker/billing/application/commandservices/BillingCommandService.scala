package cargotracker.billing.application.commandservices

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.repositories.InvoiceRepository
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  Money as BillingMoney
}
import cargotracker.booking.domain.model.repositories.CargoRepository
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus}
import cargotracker.shared.domain.pricing.PricingService

import java.time.Clock
import javax.inject.{Inject, Singleton}

/** 請求書発行コマンドサービス（US21 / IT6）。
  *
  *   - `BookingStatus.Delivered` の予約のみ請求可
  *   - 既に請求済の予約は冪等成功（既存 Invoice を返す）
  *   - PricingService で実費 (`calculateActual`) を算出し `Invoice` を `Pending` で発行
  */
@Singleton
class BillingCommandService @Inject() (
    invoiceRepository: InvoiceRepository,
    cargoRepository: CargoRepository,
    pricingService: PricingService,
    clock: Clock
):

  def generate(command: GenerateInvoiceCommand): Either[String, Invoice] =
    for
      id <- BookingId(command.bookingId).left.map(_ => s"予約 ID の形式が不正です: ${command.bookingId}")
      cargo <- cargoRepository.findById(id).toRight(s"予約 ${command.bookingId} が見つかりません")
      _ <- Either.cond(
        cargo.status == BookingStatus.Delivered,
        (),
        s"予約は Delivered 状態である必要があります（現在: ${cargo.status}）"
      )
      bid = BillingBookingId.unsafeFrom(cargo.bookingId.value)
      existing = invoiceRepository.findByBookingId(bid)
      result <- existing match
        case Some(inv) => Right(inv) // 冪等成功
        case None =>
          for
            base <- pricingService
              .calculateActual(
                cargo.routeSpecification.origin,
                cargo.routeSpecification.destination,
                cargo.cargoSpec.cargoType,
                cargo.cargoSpec.weight,
                cargo.itinerary.map(_.voyageNumbers).getOrElse(Nil)
              )
              .left
              .map(_ => "料金算出に失敗しました")
            baseMoney: BillingMoney = BillingMoney.unsafeFrom(base.amount)
            shipper = BillingShipperId(cargo.shipperId.value, command.isCorporate)
            invoice <- Invoice
              .issue(
                invoiceRepository.nextInvoiceId(),
                bid,
                shipper,
                baseMoney,
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
