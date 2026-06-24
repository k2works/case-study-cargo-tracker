package cargotracker.shared.audit.infrastructure

import cargotracker.shared.audit.domain.*
import cargotracker.support.{DbCleanupSupport, PostgresContainerSupport}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}

import java.time.Instant

/** IT9 US30: ScalikeJdbcAuditLogAdapter の IT。
  *
  *   - record + findById で round-trip 確認
  *   - findByFilter (日付 / アクター / 操作) の絞り込み
  *   - 不変記録: UPDATE / DELETE が API として提供されないことを Adapter 構造で確認 (コンパイル時保証)
  */
class ScalikeJdbcAuditLogAdapterIntegrationSpec
    extends AnyFunSuite
    with Matchers
    with EitherValues
    with OptionValues
    with PostgresContainerSupport
    with DbCleanupSupport:

  override protected def cleanupTables: Seq[String] = super.cleanupTables :+ "audit_log"

  test("record + findById: 監査ログを記録して ID で取得できる (IT9 US30)"):
    val adapter = new ScalikeJdbcAuditLogAdapter
    val id = adapter
      .record(
        operator = "sato",
        action = AuditAction.ConfirmPayment,
        targetType = "Invoice",
        targetId = "INV-000001",
        before = Some("""{"paymentStatus":"Pending"}"""),
        after = Some("""{"paymentStatus":"Confirmed","paidAt":"2026-10-15T09:00:00Z"}""")
      )
      .value
    val loaded = adapter.findById(id).value
    loaded.operator shouldBe "sato"
    loaded.action shouldBe AuditAction.ConfirmPayment
    loaded.targetType shouldBe "Invoice"
    loaded.targetId shouldBe "INV-000001"
    loaded.before.value should include("Pending")
    loaded.after.value should include("Confirmed")

  test("findByFilter: フィルタなしで全件を occurredAt DESC 順に返却 (IT9 US30)"):
    val adapter = new ScalikeJdbcAuditLogAdapter
    adapter.record("sato", AuditAction.IssuePayment, "Invoice", "INV-A", None, None).value
    Thread.sleep(10) // occurredAt の順序を保証するため
    adapter.record("tanaka", AuditAction.ConfirmPayment, "Invoice", "INV-A", None, None).value
    val all = adapter.findByFilter(AuditLogFilter())
    all should have size 2
    all.head.operator shouldBe "tanaka" // 新しい方が先
    all(1).operator shouldBe "sato"

  test("findByFilter: action でフィルタ絞り込み (IT9 US30)"):
    val adapter = new ScalikeJdbcAuditLogAdapter
    adapter.record("sato", AuditAction.IssuePayment, "Invoice", "INV-A", None, None).value
    adapter.record("sato", AuditAction.ConfirmPayment, "Invoice", "INV-A", None, None).value
    adapter.record("sato", AuditAction.Refund, "Invoice", "INV-A", None, None).value
    val filtered = adapter.findByFilter(AuditLogFilter(action = Some(AuditAction.ConfirmPayment)))
    filtered should have size 1
    filtered.head.action shouldBe AuditAction.ConfirmPayment

  test("findByFilter: operator でフィルタ絞り込み (IT9 US30)"):
    val adapter = new ScalikeJdbcAuditLogAdapter
    adapter.record("sato", AuditAction.IssuePayment, "Invoice", "INV-A", None, None).value
    adapter.record("tanaka", AuditAction.IssuePayment, "Invoice", "INV-B", None, None).value
    val filtered = adapter.findByFilter(AuditLogFilter(operator = Some("tanaka")))
    filtered should have size 1
    filtered.head.operator shouldBe "tanaka"

  test("findByFilter: limit 指定が反映される (IT9 US30)"):
    val adapter = new ScalikeJdbcAuditLogAdapter
    (1 to 5).foreach(i => adapter.record(s"u$i", AuditAction.IssuePayment, "Invoice", s"INV-$i", None, None).value)
    adapter.findByFilter(AuditLogFilter(limit = 3)) should have size 3
