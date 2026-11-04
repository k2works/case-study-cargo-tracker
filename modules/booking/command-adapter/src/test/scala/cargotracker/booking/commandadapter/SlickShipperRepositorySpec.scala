package cargotracker.booking.commandadapter

import cargotracker.booking.domain.*
import cargotracker.booking.usecase.AuditStamp
import cargotracker.sharedkernel.{Clock, Ulid}
import cargotracker.testsupport.TestDatabase
import java.time.Instant
import java.util.Random
import org.scalatest.funsuite.AnyFunSuite
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import slick.jdbc.PostgresProfile.api.*

/** 荷主の DAO（書き込み側の表。PostgreSQL の実物で検査する）。 */
class SlickShipperRepositorySpec extends AnyFunSuite:
  given ExecutionContext = ExecutionContext.global

  private val at = Instant.parse("2026-11-04T00:00:00Z")
  private val clock = Clock.fixed(at)
  private val rnd = new Random(21)
  private def ulid() = Ulid.generate(clock, rnd)
  private val actor = ulid()

  private final class Env:
    val testDb = TestDatabase.fresh()
    val db =
      Database.forURL(testDb.url, testDb.user, testDb.password, driver = "org.postgresql.Driver")
    val repo = new SlickShipperRepository(db)
    def sql(statement: String): Int = Await.result(db.run(sqlu"#$statement"), 5.seconds)
    def count(table: String): Int =
      Await.result(db.run(sql"SELECT count(*) FROM #$table".as[Int].head), 5.seconds)

  private def shipper(
      id: ShipperId = ShipperId(ulid()),
      email: String = "taro@example.com",
      address: String = "東京都千代田区"
  ) =
    Shipper(
      id,
      ShipperKind.Individual,
      ShipperName.parse("山田 太郎").toOption.get,
      Address.parse(address).toOption.get,
      ContactNumber.parse("03-1234-5678").toOption.get,
      EmailAddress.parse(email).toOption.get
    )

  private def await[A](f: scala.concurrent.Future[A]): A = Await.result(f, 5.seconds)

  test("保存した荷主を、ID で取り出すと元と同じ") {
    val env = new Env
    val s = shipper()
    await(env.repo.save(s, AuditStamp(actor, at)))
    assert(await(env.repo.findById(s.id)) == Some(s))
  }

  test("存在しない ID は None") {
    val env = new Env
    assert(await(env.repo.findById(ShipperId(ulid()))).isEmpty)
  }

  test("US02-AC02: 正規化したメールアドレスで、既存の荷主を探せる（大文字小文字を区別しない）") {
    val env = new Env
    val s = shipper(email = "Taro@Example.COM")
    await(env.repo.save(s, AuditStamp(actor, at)))
    assert(
      await(env.repo.findByEmail(EmailAddress.parse("taro@example.com").toOption.get)) == Some(s.id)
    )
    assert(
      await(env.repo.findByEmail(EmailAddress.parse("other@example.com").toOption.get)).isEmpty
    )
  }

  test("US02-AC02: 同じメールの荷主を 2 件保存でき（一意制約にしない）、先に登録した荷主を示す") {
    val env = new Env
    val first = shipper()
    val later = shipper()
    await(env.repo.save(first, AuditStamp(actor, at)))
    await(env.repo.save(later, AuditStamp(actor, at.plusSeconds(60))))
    assert(env.count("booking.shipper") == 2)
    assert(await(env.repo.findByEmail(first.email)) == Some(first.id))
  }

  test("同じ ID の保存は更新になり、行は増えない。作成の日時・者は変わらず、更新の日時・者が進む") {
    val env = new Env
    val s = shipper()
    val other = ulid()
    await(env.repo.save(s, AuditStamp(actor, at)))
    await(
      env.repo.save(
        s.copy(address = Address.parse("大阪府大阪市").toOption.get),
        AuditStamp(other, at.plusSeconds(3600))
      )
    )
    assert(env.count("booking.shipper") == 1)
    assert(await(env.repo.findById(s.id)).get.address.value == "大阪府大阪市")
    val audit = Await.result(
      env.db.run(
        sql"SELECT created_by, updated_by, created_at = ${java.sql.Timestamp.from(at)}, updated_at > created_at FROM booking.shipper"
          .as[(String, String, Boolean, Boolean)]
          .head
      ),
      5.seconds
    )
    assert(audit == (actor.value, other.value, true, true))
  }

  test("論理削除された荷主は、ID でもメールでも見つからない（DB-06）") {
    val env = new Env
    val s = shipper()
    await(env.repo.save(s, AuditStamp(actor, at)))
    env.sql(
      s"UPDATE booking.shipper SET deleted_at = now() WHERE shipper_id = '${s.id.value.value}'"
    )
    assert(await(env.repo.findById(s.id)).isEmpty)
    assert(await(env.repo.findByEmail(s.email)).isEmpty)
  }
