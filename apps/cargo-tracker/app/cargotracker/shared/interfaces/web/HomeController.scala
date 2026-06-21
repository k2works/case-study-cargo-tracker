package cargotracker.shared.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}

import javax.inject.{Inject, Singleton}

/** ロール別ダッシュボード（IT2 タスク 0.2）。
  *
  * `AuthenticatedAction` を介して取得したユーザー情報（username / roles）を Twirl テンプレートに渡し、表示メニューをロールで切り替える。
  *   - Sales: 見積 / 荷主 / 貨物予約
  *   - RouteDesigner: 引き渡し済み予約一覧（IT3 で本格実装）+ 航海スケジュール（IT2 US24/25 で追加）
  *   - Tracker / Settlement / MasterAdmin: 各ロール向けカード（IT3+ で実装）
  */
@Singleton
class HomeController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction
) extends AbstractController(cc):

  def index(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.dashboard(request.username, request.roles))
  }
