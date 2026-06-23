package cargotracker.billing.infrastructure.repositories

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.repositories.InvoiceRepository
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  InvoiceId,
  Money
}
import cargotracker.shared.domain.OptimisticLockException
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcInvoiceRepository extends InvoiceRepository:

  private def rowTo(rs: WrappedResultSet): Option[Invoice] =
    for status <- PaymentStatus.fromName(rs.string("payment_status"))
    yield Invoice.reconstruct(
      invoiceId = InvoiceId.unsafeFrom(rs.string("invoice_number")),
      cargoBookingId = BillingBookingId.unsafeFrom(rs.string("booking_id")),
      shipperId = BillingShipperId(rs.string("shipper_id"), rs.boolean("is_corporate")),
      baseAmount = Money.unsafeFrom(rs.long("base_amount")),
      discountRate = DiscountRate.unsafeFrom(rs.bigDecimal("discount_rate")),
      finalAmount = Money.unsafeFrom(rs.long("final_amount")),
      paymentStatus = status,
      issuedAt = rs.zonedDateTime("issued_at").toInstant,
      paidAt = rs.zonedDateTimeOpt("paid_at").map(_.toInstant),
      version = rs.int("version")
    )

  override def nextInvoiceId(): InvoiceId =
    DB.readOnly { implicit session =>
      val n = sql"SELECT nextval('invoice_id_seq') AS next"
        .map(_.long("next"))
        .single
        .apply()
        .getOrElse(throw IllegalStateException("invoice_id_seq から採番できませんでした"))
      InvoiceId.fromSequence(n)
    }

  override def findById(id: InvoiceId): Option[Invoice] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM invoice WHERE invoice_number = ${id.value}"
        .map(rowTo)
        .single
        .apply()
        .flatten
    }

  override def findByBookingId(bookingId: BillingBookingId): Option[Invoice] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM invoice WHERE booking_id = ${bookingId.value}"
        .map(rowTo)
        .single
        .apply()
        .flatten
    }

  override def findAll(): Seq[Invoice] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM invoice ORDER BY issued_at DESC".map(rowTo).list.apply().flatten
    }

  override def save(invoice: Invoice): Unit =
    DB.localTx { implicit session =>
      val existing =
        sql"SELECT id FROM invoice WHERE invoice_number = ${invoice.invoiceId.value}"
          .map(_.long("id"))
          .single
          .apply()

      existing match
        case None =>
          sql"""
            INSERT INTO invoice
              (invoice_number, booking_id, shipper_id, is_corporate,
               base_amount, discount_rate, final_amount, payment_status, issued_at)
            VALUES
              (${invoice.invoiceId.value},
               ${invoice.cargoBookingId.value},
               ${invoice.shipperId.value},
               ${invoice.shipperId.isCorporate},
               ${invoice.baseAmount.value},
               ${invoice.discountRate.value},
               ${invoice.finalAmount.value},
               ${invoice.paymentStatus.toString},
               ${java.sql.Timestamp.from(invoice.issuedAt)})
          """.update.apply()
        case Some(_) =>
          val updated = sql"""
            UPDATE invoice
            SET payment_status = ${invoice.paymentStatus.toString},
                paid_at = ${invoice.paidAt.map(java.sql.Timestamp.from).orNull},
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE invoice_number = ${invoice.invoiceId.value} AND version = ${invoice.version}
          """.update.apply()
          if updated == 0 then
            throw OptimisticLockException(
              entityType = "Invoice",
              identifier = invoice.invoiceId.value
            )
    }
