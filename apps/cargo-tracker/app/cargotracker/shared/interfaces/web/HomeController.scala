package cargotracker.shared.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.queryservices.BookingQueryService
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}

/** ロール別ダッシュボード（IT2 タスク 0.2 + 2.3）。
  *
  * 集計ロジックは [[DashboardComposer]] に切り出された pure function 群を呼び出す（IT3 タスク 0.6）。 Controller は副作用（DB 取得関数と Twirl
  * レンダリング）の組み立てのみ行う。
  */
@Singleton
class HomeController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    bookingQuery: BookingQueryService
) extends AbstractController(cc):

  def index(): Action[AnyContent] = authenticated { implicit request =>
    val view = DashboardComposer.compose(
      username = request.username,
      roles = request.roles,
      fetchRouteProposed = () => bookingQuery.findRouteProposed(),
      fetchRouteAssigned = () => bookingQuery.findRouteAssigned()
    )
    Ok(views.html.dashboard(view.username, view.roles, view.routeProposed, view.routeAssigned))
  }
