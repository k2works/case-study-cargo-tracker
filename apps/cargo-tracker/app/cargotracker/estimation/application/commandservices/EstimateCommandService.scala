package cargotracker.estimation.application.commandservices

import cargotracker.estimation.domain.model.aggregates.Estimate
import cargotracker.estimation.domain.model.repositories.EstimateRepository
import cargotracker.estimation.domain.model.valueobjects.RouteCandidate
import cargotracker.shared.domain.pricing.PricingService
import cargotracker.shared.domain.{CargoType, Location, Weight}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

/** 輸送見積コマンドサービス（US01）。
  *
  * UnLocode / 貨物種別 / 重量の検証、料金算出（[[PricingService]]）、ルート候補の組み立て、 [[Estimate]] 集約の生成・永続化までを一連のユースケースとして実行する。
  */
@Singleton
class EstimateCommandService @Inject() (
    repository: EstimateRepository,
    pricingService: PricingService
):

  def create(command: CreateEstimateCommand): Either[String, Estimate] =
    val result =
      for
        origin <- Location(command.origin).left.map(_ => "出発地の UnLocode 形式が不正です")
        destination <- Location(command.destination).left
          .map(_ => "目的地の UnLocode 形式が不正です")
        cargoType <- CargoType
          .fromName(command.cargoType)
          .toRight("貨物種別が不正です")
        weight <- Weight(command.weightKg).left.map(_ => "重量が不正です")
        cost <- pricingService
          .estimateCost(origin, destination, cargoType, weight, None)
          .left
          .map(_ => "料金算出に失敗しました（出発地と目的地が同一の可能性）")
        candidate = RouteCandidate(
          voyageNumber = "VY-MOCK-001",
          transitPorts = List(origin.unLocode, destination.unLocode),
          transitDays = 14,
          estimatedCost = cost
        )
        estimate <- Estimate
          .create(origin, destination, command.deadline, cargoType, weight, List(candidate))
          .left
          .map(_ => "見積の生成に失敗しました")
      yield estimate

    result.map { estimate =>
      repository.save(estimate)
      estimate
    }

/** 輸送見積作成コマンド（US01）。Controller から受け取るフォーム値の DTO 相当。 */
final case class CreateEstimateCommand(
    origin: String,
    destination: String,
    deadline: LocalDate,
    cargoType: String,
    weightKg: Long
)
