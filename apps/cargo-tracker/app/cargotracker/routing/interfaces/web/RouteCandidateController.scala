package cargotracker.routing.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.queryservices.BookingQueryService
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.routing.application.commandservices.{RoutingCommandService, SelectRouteCommand}
import cargotracker.routing.application.queryservices.{
  CalculateRouteCommand,
  PricedRouteCandidate,
  RouteCandidateQueryService
}
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Clock, Instant, ZoneId}
import javax.inject.{Inject, Singleton}

/** US08 経路候補一覧画面（GET /bookings/:bookingId/routes）。
  *
  * 予約情報から `CalculateRouteCommand` を組み立て、 `PricingService` 経由の料金見積もり付きで上位 N 件を取得する。 期限内に到着できない候補は `late` として分離し、画面上で通知 +
  * 条件緩和ガイドを表示する。
  */
@Singleton
class RouteCandidateController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    bookingQueryService: BookingQueryService,
    routeCandidateQueryService: RouteCandidateQueryService,
    routingCommandService: RoutingCommandService,
    clock: Clock
) extends AbstractController(cc)
    with I18nSupport:

  def candidates(bookingId: String): Action[AnyContent] = authenticated { implicit request =>
    bookingQueryService.findById(bookingId) match
      case None => NotFound("予約が見つかりません")
      case Some(cargo) =>
        val command = CalculateRouteCommand(
          origin = cargo.routeSpecification.origin.unLocode,
          destination = cargo.routeSpecification.destination.unLocode,
          earliestDeparture = Instant.now(clock),
          cargoType = Some(cargo.cargoSpec.cargoType.toString),
          weightKg = Some(cargo.cargoSpec.weight.kg)
        )
        routeCandidateQueryService.calculateCandidates(command) match
          case Right(all) =>
            val (onTime, late) = splitByDeadline(cargo, all)
            Ok(views.html.booking.routes(cargo, onTime, late, errorMessage = None))
          case Left(msg) =>
            BadRequest(
              views.html.booking
                .routes(
                  cargo,
                  List.empty[PricedRouteCandidate],
                  List.empty[PricedRouteCandidate],
                  Some(msg)
                )
            )
  }

  /** US09: 経路候補画面の「この経路で確定」ボタンの POST 受口（PRG）。
    *
    * 期限内候補を画面と同じ順序で再算出し、`idx` 番目の航海列を `SelectRouteCommand` として確定する。
    */
  def confirm(bookingId: String, idx: Int): Action[AnyContent] = authenticated { implicit request =>
    bookingQueryService.findById(bookingId) match
      case None => NotFound("予約が見つかりません")
      case Some(cargo) =>
        val command = CalculateRouteCommand(
          origin = cargo.routeSpecification.origin.unLocode,
          destination = cargo.routeSpecification.destination.unLocode,
          earliestDeparture = Instant.now(clock),
          cargoType = Some(cargo.cargoSpec.cargoType.toString),
          weightKg = Some(cargo.cargoSpec.weight.kg)
        )
        routeCandidateQueryService.calculateCandidates(command) match
          case Left(msg) =>
            Redirect(routes.RouteCandidateController.candidates(bookingId)).flashing("error" -> msg)
          case Right(all) =>
            val (onTime, _) = splitByDeadline(cargo, all)
            onTime.lift(idx) match
              case None =>
                Redirect(routes.RouteCandidateController.candidates(bookingId))
                  .flashing("error" -> "選択された経路候補が見つかりません。再度候補を確認してください")
              case Some(picked) =>
                val voyageNumbers = picked.candidate.voyages.map(_.value)
                routingCommandService.confirmRoute(SelectRouteCommand(bookingId, voyageNumbers)) match
                  case Right(_) =>
                    Redirect(routes.RouteCandidateController.candidates(bookingId))
                      .flashing("success" -> "経路を確定しました")
                  case Left(msg) =>
                    Redirect(routes.RouteCandidateController.candidates(bookingId)).flashing("error" -> msg)
  }

  /** 希望着日（end-of-day）までに到着できる候補と期限超過候補に分離する。 */
  private def splitByDeadline(
      cargo: Cargo,
      candidates: List[PricedRouteCandidate]
  ): (List[PricedRouteCandidate], List[PricedRouteCandidate]) =
    val deadline = cargo.routeSpecification.arrivalDeadline
      .atTime(23, 59, 59)
      .atZone(ZoneId.systemDefault())
      .toInstant
    candidates.partition(p => !p.candidate.arrival.isAfter(deadline))
