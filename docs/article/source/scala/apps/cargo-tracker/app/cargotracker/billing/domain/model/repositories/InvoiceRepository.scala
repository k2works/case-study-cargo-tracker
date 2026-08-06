package cargotracker.billing.domain.model.repositories

import cargotracker.billing.domain.model.aggregates.Invoice
import cargotracker.billing.domain.model.valueobjects.{BillingBookingId, InvoiceId}

/** Billing Context のリポジトリポート（ヘキサゴナル / US21）。 */
trait InvoiceRepository:
  def nextInvoiceId(): InvoiceId
  def findById(id: InvoiceId): Option[Invoice]
  def findByBookingId(bookingId: BillingBookingId): Option[Invoice]
  def findAll(): Seq[Invoice]
  def save(invoice: Invoice): Unit
