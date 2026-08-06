package cargotracker.shared.application

import scalikejdbc.{AutoSession, DB, DBSession}

import javax.inject.{Inject, Singleton}

/** IT9 0.1 (ADR 0016 案 A): TX 境界の抽象化。
  *
  *   - 本番: ScalikeJdbcTransactionBoundary が `DB.localTx { ... }` で実 TX を開く
  *   - テスト: InMemory な NoOpTransactionBoundary が AutoSession を渡して TX なしで実行
  *   - これにより HandlingOrchestrator のロジックを TX に依存しない形でテスト可能
  */
trait TransactionBoundary:
  def inLocalTx[A](block: DBSession => A): A

@Singleton
class ScalikeJdbcTransactionBoundary @Inject() () extends TransactionBoundary:
  override def inLocalTx[A](block: DBSession => A): A = DB.localTx(block)

/** テスト用 no-op 実装: AutoSession を block に渡すのみ (本物の TX は構成しない)。 */
final class NoOpTransactionBoundary extends TransactionBoundary:
  override def inLocalTx[A](block: DBSession => A): A = block(AutoSession)
