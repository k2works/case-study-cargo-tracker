package cargotracker.routing.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.queryservices.BookingQueryService
import cargotracker.routing.application.queryservices.{CalculateRouteCommand, RouteCandidateQueryService}
import cargotracker.routing.domain.model.valueobjects.RouteCandidate
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}

/** US08 経路候補一覧画面（GET /bookings/:bookingId/routes）。
  *
  * 予約情報（出発地・目的地・貨物種別・希望着日）から `CalculateRouteCommand` を組み立て、 `RouteCandidateQueryService` で上位 N 件を取得して表示する。
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
          cargoType = Some(cargo.cargoSpec.cargoType.toString)
        )
        routeCandidateQueryService.calculateCandidates(command) match
          case Right(candidates) =>
            Ok(views.html.booking.routes(cargo, candidates, errorMessage = None))
          case Left(msg) =>
            BadRequest(views.html.booking.routes(cargo, List.empty[RouteCandidate], Some(msg)))
  }
