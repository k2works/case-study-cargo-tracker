package cargotracker.tracking.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.tracking.application.queryservices.TrackingQueryService
import play.api.i18n.I18nSupport
import play.api.mvc.*

import javax.inject.{Inject, Singleton}

/** 認証ユーザー向け追跡照会画面（US18）。 */
@Singleton
class TrackingController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    queryService: TrackingQueryService
) extends AbstractController(cc)
    with I18nSupport:

  /** 追跡番号入力フォーム。 */
  def input(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.tracking.input())
  }

  /** 入力された追跡番号で照会し、詳細画面にリダイレクト。 */
  def lookup(): Action[AnyContent] = authenticated { implicit request =>
    request.body.asFormUrlEncoded.flatMap(_.get("trackingNumber").flatMap(_.headOption)) match
      case Some(tn) if tn.nonEmpty =>
        Redirect(routes.TrackingController.detail(tn))
      case _ =>
        Redirect(routes.TrackingController.input())
          .flashing("error" -> "追跡番号を入力してください")
  }

  /** 追跡詳細（30 秒 htmx ポーリングでタイムライン更新）。 */
  def detail(trackingNumber: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findByTrackingNumber(trackingNumber) match
      case Some(view) => Ok(views.html.tracking.detail(view))
      case None =>
        Redirect(routes.TrackingController.input())
          .flashing("error" -> s"追跡番号 $trackingNumber が見つかりません")
  }

  /** タイムライン部分（htmx 部分更新）。 */
  def timeline(trackingNumber: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findByTrackingNumber(trackingNumber) match
      case Some(view) => Ok(views.html.tracking._timeline(view))
      case None => NotFound("追跡番号が見つかりません")
  }
