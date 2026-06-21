package cargotracker.routing.application.commandservices

import cargotracker.routing.domain.model.aggregates.Voyage
import cargotracker.routing.domain.model.repositories.VoyageRepository
import cargotracker.routing.domain.model.valueobjects.{CarrierMovement, Schedule, VoyageNumber}
import cargotracker.shared.domain.Location

import java.time.Instant
import javax.inject.{Inject, Singleton}

/** 航海スケジュール登録・更新のコマンドサービス（US24・US25）。
  *
  *   - register: 同 VoyageNumber 重複は `Left("...は既に登録されています")`
  *   - update: 既存 VoyageNumber 必須、未登録は `Left("...が見つかりません")`
  *   - 入力 DTO の Location / Instant 変換と Schedule の組み立てを行う
  */
@Singleton
class VoyageCommandService @Inject() (repository: VoyageRepository):

  def register(command: RegisterVoyageCommand): Either[String, Voyage] =
    upsert(
      command.voyageNumber,
      command.movements,
      existence = {
        case Some(_) => Left(s"航海番号 ${command.voyageNumber} は既に登録されています")
        case None => Right(None)
      },
      build = (vn, _, schedule) => Voyage.register(vn, schedule)
    )

  def update(command: UpdateVoyageCommand): Either[String, Voyage] =
    upsert(
      command.voyageNumber,
      command.movements,
      existence = {
        case Some(v) => Right(Some(v))
        case None => Left(s"航海番号 ${command.voyageNumber} が見つかりません")
      },
      build = (_, existing, schedule) => existing.get.updateSchedule(schedule)
    )

  /** register / update の共通骨格（IT2 review #6 / IT3 タスク 0.3）。
    *
    *   - 航海番号パース → 既存検索 + 存在チェック → スケジュール組み立て → 集約構築 → 保存
    */
  private def upsert(
      voyageNumberRaw: String,
      movements: Seq[CarrierMovementCommand],
      existence: Option[Voyage] => Either[String, Option[Voyage]],
      build: (VoyageNumber, Option[Voyage], Schedule) => Voyage
  ): Either[String, Voyage] =
    for
      vn <- VoyageNumber(voyageNumberRaw).left.map(_ => "航海番号の形式が不正です")
      existing <- existence(repository.findByVoyageNumber(vn))
      schedule <- buildSchedule(movements)
      voyage = build(vn, existing, schedule)
    yield
      repository.save(voyage)
      voyage

  private def buildSchedule(
      inputs: Seq[CarrierMovementCommand]
  ): Either[String, Schedule] =
    if inputs.isEmpty then Left("運送区間を 1 つ以上指定してください")
    else
      val movementsE: Either[String, List[CarrierMovement]] = inputs.toList
        .foldLeft[Either[String, List[CarrierMovement]]](Right(Nil)) { (acc, in) =>
          for
            built <- acc
            origin <- Location(in.departureLocation).left
              .map(_ => s"出発地の UnLocode 形式が不正です: ${in.departureLocation}")
            dest <- Location(in.arrivalLocation).left
              .map(_ => s"到着地の UnLocode 形式が不正です: ${in.arrivalLocation}")
            cm <- CarrierMovement(origin, dest, in.departureTime, in.arrivalTime).left
              .map {
                case CarrierMovement.SameOriginAndDestination => "出発地と到着地が同じ区間があります"
                case CarrierMovement.InvalidTimeRange => "出発時刻が到着時刻以降の区間があります"
              }
          yield built :+ cm
        }
      movementsE.flatMap { ms =>
        Schedule(ms).left.map {
          case Schedule.EmptyMovements => "運送区間を 1 つ以上指定してください"
          case Schedule.DisconnectedMovements =>
            "区間が連続していません（前区間の到着地と次区間の出発地が一致しません）"
          case Schedule.OutOfOrderTimes => "区間の時刻が時系列順になっていません"
        }
      }

/** 航海スケジュール登録コマンド（US24）。 */
final case class RegisterVoyageCommand(
    voyageNumber: String,
    movements: Seq[CarrierMovementCommand]
)

/** 航海スケジュール更新コマンド（US25）。 */
final case class UpdateVoyageCommand(
    voyageNumber: String,
    movements: Seq[CarrierMovementCommand]
)

/** Controller フォームからの運送区間入力 DTO。 */
final case class CarrierMovementCommand(
    departureLocation: String,
    arrivalLocation: String,
    departureTime: Instant,
    arrivalTime: Instant
)
