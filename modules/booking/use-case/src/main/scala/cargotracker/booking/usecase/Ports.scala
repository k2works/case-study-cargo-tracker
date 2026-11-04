package cargotracker.booking.usecase

import cargotracker.booking.domain.*
import cargotracker.sharedkernel.{PortCode, Ulid}
import scala.concurrent.Future

/** 出力ポート。ユースケースが定義し、adapter が実装する（ADR-001。ユースケースは Pekko を知らない）。 */

trait ShipperRepository:
  /** 同じメールアドレスの既存の荷主（重複の警告のための外部の事実。US02 AC-02）。 */
  def findByEmail(email: EmailAddress): Future[Option[ShipperId]]
  def findById(id: ShipperId): Future[Option[Shipper]]

  /** 新規は挿入、同じ ID は更新（作成の日時・者は変えない）。`audit` は作成・更新の日時と者。 */
  def save(shipper: Shipper, audit: AuditStamp): Future[Unit]

/** 書き込み側の表に残す、操作者と日時。 */
final case class AuditStamp(by: Ulid, at: java.time.Instant)

/** 冪等性キーと、最初の結果の保存先（ADR-003。`platform.idempotency_key`）。 */
/** 参照データ（reference.port）にある港か。形式が正しいだけの存在しないコードを、書き込み側で拒むための外部の事実。 */
trait PortCatalog:
  def exists(code: PortCode): Future[Boolean]

trait IdempotencyStore:
  def find(owner: Ulid, key: String): Future[Option[StoredResult]]
  def save(owner: Ulid, key: String, result: StoredResult): Future[Unit]

/** 保存する最初の結果。`requestHash` は、同じキーで内容が違う要求を見分けるため。 */
final case class StoredResult(requestHash: String, output: IdempotentOutput)

enum IdempotentOutput:
  case ShipperRegistered(shipperId: ShipperId)
  case BookingRegistered(bookingId: BookingId, number: BookingNumber, version: Long)

/** 集約アクターへのポート。結果には最後のイベントの版を含める（画面が「版が追いつくまで」待つため。BE 6.4）。 */
trait BookingAggregates:
  def handle(id: BookingId, command: BookingCommand): Future[Either[BookingError, CommandResult]]

final case class CommandResult(lastVersion: Long)

/** 人が読む予約番号の発行（形式は未定義で、整形はここに閉じ込める。Bolt 計画 Q-2）。 */
trait BookingNumberIssuer:
  def next(): Future[BookingNumber]

/** 集約 ID（ULID）の生成。テストで再現できるようにポートにする。 */
trait IdGenerator:
  def next(): Ulid
