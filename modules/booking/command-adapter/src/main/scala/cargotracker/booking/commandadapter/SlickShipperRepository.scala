package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.booking.usecase.{AuditStamp, ShipperRepository}
import cargotracker.sharedkernel.Ulid
import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api.*

/** 荷主の DAO（`booking.shipper`。手書きの Slick 定義。ADR-008）。個人情報はこの表にだけ置く（ADR-004）。 */
final class SlickShipperRepository(db: Database)(using ExecutionContext) extends ShipperRepository:
  import SlickShipperRepository.*

  def findByEmail(email: EmailAddress): Future[Option[ShipperId]] =
    // 同じメールの荷主が複数いてよい（一意制約にしない）。先に登録した荷主を示す。
    db.run(
      shippers
        .filter(r => r.emailNormalized === email.value && r.deletedAt.isEmpty)
        .sortBy(r => (r.createdAt, r.shipperId))
        .map(_.shipperId)
        .result
        .headOption
    ).map(_.map(id => ShipperId(unsafeUlid(id))))

  def findById(id: ShipperId): Future[Option[Shipper]] =
    db.run(
      shippers.filter(r => r.shipperId === id.value.value && r.deletedAt.isEmpty).result.headOption
    ).map(_.map(toDomain))

  def save(shipper: Shipper, audit: AuditStamp): Future[Unit] =
    val at = audit.at
    val id = shipper.id.value.value
    // 新規は挿入、既存は更新（常に挿入する保存は、更新で行を増やす）。作成の日時・者は更新しない。
    val upsert = for
      updated <- shippers
        .filter(_.shipperId === id)
        .map(r =>
          (r.name, r.address, r.contact, r.email, r.emailNormalized, r.updatedAt, r.updatedBy)
        )
        .update(
          (
            shipper.name.value,
            shipper.address.value,
            shipper.contact.value,
            shipper.email.value,
            shipper.email.value,
            at,
            audit.by.value
          )
        )
      _ <-
        if updated > 0 then DBIO.successful(())
        else
          (shippers += Row(
            id,
            "INDIVIDUAL",
            shipper.name.value,
            shipper.address.value,
            shipper.contact.value,
            shipper.email.value,
            shipper.email.value,
            None,
            at,
            audit.by.value,
            at,
            audit.by.value
          )).map(_ => ())
    yield ()
    db.run(upsert.transactionally)

object SlickShipperRepository:

  /** `timestamptz` ↔ `Instant`。Slick 標準の java.time 対応は、PostgreSQL では文字列として束縛されて型が合わないため、
    * `java.sql.Timestamp`（瞬間として扱われる）を介する。
    */
  private given BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](Timestamp.from, _.toInstant)

  final case class Row(
      shipperId: String,
      shipperType: String,
      name: String,
      address: String,
      contact: String,
      email: String,
      emailNormalized: String,
      deletedAt: Option[Instant],
      createdAt: Instant,
      createdBy: String,
      updatedAt: Instant,
      updatedBy: String
  )

  final class ShipperTable(tag: Tag) extends Table[Row](tag, Some("booking"), "shipper"):
    def shipperId = column[String]("shipper_id", O.PrimaryKey)
    def shipperType = column[String]("shipper_type")
    def name = column[String]("name")
    def address = column[String]("address")
    def contact = column[String]("contact")
    def email = column[String]("email")
    def emailNormalized = column[String]("email_normalized")
    def deletedAt = column[Option[Instant]]("deleted_at")
    def createdAt = column[Instant]("created_at")
    def createdBy = column[String]("created_by")
    def updatedAt = column[Instant]("updated_at")
    def updatedBy = column[String]("updated_by")
    def * = (
      shipperId,
      shipperType,
      name,
      address,
      contact,
      email,
      emailNormalized,
      deletedAt,
      createdAt,
      createdBy,
      updatedAt,
      updatedBy
    )
      .mapTo[Row]

  val shippers = TableQuery[ShipperTable]

  private def unsafeUlid(raw: String): Ulid =
    Ulid
      .parse(raw.trim)
      .fold(e => throw new IllegalStateException(s"DB の荷主 ID が不正: $raw (${e.message})"), identity)

  /** DB の行は信頼するが、壊れた行は握りつぶさずに原因が分かる例外で失敗させる。 */
  private def toDomain(r: Row): Shipper =
    def corrupt(e: cargotracker.sharedkernel.InvalidValue) =
      new IllegalStateException(s"DB の荷主 ${r.shipperId} が不正: ${e.message}")
    val kind = r.shipperType match
      case "INDIVIDUAL" => ShipperKind.Individual
      case other        => throw new IllegalStateException(s"DB の荷主 ${r.shipperId} の種別が不明: $other")
    Shipper(
      ShipperId(unsafeUlid(r.shipperId)),
      kind,
      ShipperName.parse(r.name).fold(e => throw corrupt(e), identity),
      Address.parse(r.address).fold(e => throw corrupt(e), identity),
      ContactNumber.parse(r.contact).fold(e => throw corrupt(e), identity),
      EmailAddress.parse(r.email).fold(e => throw corrupt(e), identity)
    )
