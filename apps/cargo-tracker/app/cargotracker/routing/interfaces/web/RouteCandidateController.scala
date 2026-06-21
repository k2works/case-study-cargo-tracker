package cargotracker.routing.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.queryservices.BookingQueryService
import cargotracker.booking.domain.model.aggregates.Cargo
import cargotracker.routing.application.{CalculateRouteCommand, PricedRouteCandidate}
import cargotracker.routing.application.queryservices.RouteCandidateQueryService
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
