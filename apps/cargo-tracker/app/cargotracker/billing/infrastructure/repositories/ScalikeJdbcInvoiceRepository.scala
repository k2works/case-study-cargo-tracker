package cargotracker.billing.infrastructure.repositories

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.enums.PaymentStatus
import cargotracker.billing.domain.model.repositories.InvoiceRepository
import cargotracker.billing.domain.model.valueobjects.{
  BillingBookingId,
  BillingShipperId,
  DiscountRate,
  InvoiceId,
  InvoiceLineItem,
  LineItemCategory
}
import cargotracker.shared.domain.{Money, OptimisticLockException}
import scalikejdbc.*

import javax.inject.Singleton

@Singleton
class ScalikeJdbcInvoiceRepository extends InvoiceRepository:

  private def rowToInvoiceAndId(rs: WrappedResultSet): Option[(Long, Invoice.Snapshot)] =
    for status <- PaymentStatus.fromName(rs.string("payment_status"))
    yield
      val snapshot = Invoice.Snapshot(
        invoiceId = InvoiceId.unsafeFrom(rs.string("invoice_number")),
        cargoBookingId = BillingBookingId.unsafeFrom(rs.string("booking_id")),
        shipperId = BillingShipperId(rs.string("shipper_id"), rs.boolean("is_corporate")),
        baseAmount = Money.unsafeFromJpy(rs.long("base_amount")),
        discountRate = DiscountRate.unsafeFrom(rs.bigDecimal("discount_rate")),
        finalAmount = Money.unsafeFromJpy(rs.long("final_amount")),
        paymentStatus = status,
        issuedAt = rs.zonedDateTime("issued_at").toInstant,
        paidAt = rs.zonedDateTimeOpt("paid_at").map(_.toInstant),
        version = rs.int("version")
      )
      (rs.long("id"), snapshot)

  private def loadLineItems(invoiceId: Long)(implicit session: DBSession): List[InvoiceLineItem] =
    sql"""SELECT category, description, amount
          FROM invoice_line_item
          WHERE invoice_id = $invoiceId
          ORDER BY line_no"""
      .map { rs =>
        val cat = LineItemCategory.fromName(rs.string("category")).getOrElse(LineItemCategory.Other)
        InvoiceLineItem(
          category = cat,
          name = rs.string("description"),
          amount = Money.unsafeFromJpy(rs.long("amount"))
        )
      }
      .list
      .apply()

  private def reconstructWithItems(id: Long, snapshot: Invoice.Snapshot)(implicit session: DBSession): Invoice =
    val items = loadLineItems(id)
    Invoice.reconstruct(if items.isEmpty then snapshot else snapshot.copy(lineItems = items))

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
        .map(rowToInvoiceAndId)
        .single
        .apply()
        .flatten
        .map { case (iid, snap) => reconstructWithItems(iid, snap) }
    }

  override def findByBookingId(bookingId: BillingBookingId): Option[Invoice] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM invoice WHERE booking_id = ${bookingId.value}"
        .map(rowToInvoiceAndId)
        .single
        .apply()
        .flatten
        .map { case (iid, snap) => reconstructWithItems(iid, snap) }
    }

  override def findAll(): Seq[Invoice] =
    DB.readOnly { implicit session =>
      sql"SELECT * FROM invoice ORDER BY issued_at DESC"
        .map(rowToInvoiceAndId)
        .list
        .apply()
        .flatten
        .map { case (iid, snap) => reconstructWithItems(iid, snap) }
    }

  private def replaceLineItems(invoiceId: Long, items: List[InvoiceLineItem])(implicit session: DBSession): Unit =
    sql"DELETE FROM invoice_line_item WHERE invoice_id = $invoiceId".update.apply()
    items.zipWithIndex.foreach { case (item, idx) =>
      sql"""INSERT INTO invoice_line_item
              (invoice_id, line_no, category, description, amount)
            VALUES ($invoiceId, ${idx + 1}, ${item.category.toString}, ${item.name}, ${item.amount.amount})""".update
        .apply()
    }

  override def save(invoice: Invoice): Unit =
    DB.localTx { implicit session =>
      val existing =
        sql"SELECT id FROM invoice WHERE invoice_number = ${invoice.invoiceId.value}"
          .map(_.long("id"))
          .single
          .apply()

      val invoiceId = existing match
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
               ${invoice.baseAmount.amount},
               ${invoice.discountRate.value},
               ${invoice.finalAmount.amount},
               ${invoice.paymentStatus.toString},
               ${java.sql.Timestamp.from(invoice.issuedAt)})
          """.updateAndReturnGeneratedKey.apply()
        case Some(id) =>
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
          id

      // IT7 0.9: 料金内訳を同期 (V17 で作成済 invoice_line_item テーブル + V22 で追加した category カラム)
      if invoice.lineItems.nonEmpty then replaceLineItems(invoiceId, invoice.lineItems)
    }
