package cargotracker.shared.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.queryservices.BookingQueryService
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}

/** ロール別ダッシュボード（IT2 タスク 0.2 + 2.3）。
  *
  * RouteDesigner / MasterAdmin にのみ「引き渡し済み予約一覧（RouteProposed）」を 注入する。それ以外のロールは空 Seq を渡し UI 側で非表示にする。
  */
@Singleton
class HomeController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    bookingQuery: BookingQueryService
) extends AbstractController(cc):

  def index(): Action[AnyContent] = authenticated { implicit request =>
    val routeProposed =
      if request.roles.contains(Role.RouteDesigner) || request.roles.contains(Role.MasterAdmin)
      then bookingQuery.findRouteProposed()
      else Seq.empty
    Ok(views.html.dashboard(request.username, request.roles, routeProposed))
  }
