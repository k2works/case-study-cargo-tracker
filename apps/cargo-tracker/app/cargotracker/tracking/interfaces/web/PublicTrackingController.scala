package cargotracker.tracking.interfaces.web

import cargotracker.tracking.application.queryservices.TrackingQueryService
import play.api.i18n.I18nSupport
import play.api.mvc.*

import javax.inject.{Inject, Singleton}

/** 公開追跡照会画面（US18・未認証）。CSRF 例外パスを application.conf で定義する。
  *
  *   - GET のみ許可
  *   - ナビゲーション簡素化テンプレートを使う
  */
@Singleton
class PublicTrackingController @Inject() (
    cc: ControllerComponents,
    queryService: TrackingQueryService
) extends AbstractController(cc)
    with I18nSupport:

  def detail(trackingNumber: String): Action[AnyContent] = Action { implicit request =>
    queryService.findByTrackingNumber(trackingNumber) match
      case Some(view) => Ok(views.html.tracking.publicDetail(view))
      case None => NotFound(views.html.tracking.publicNotFound(trackingNumber))
  }
