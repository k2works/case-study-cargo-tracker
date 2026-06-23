package cargotracker.tracking.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.commandservices.BookingCommandService
import cargotracker.tracking.application.commandservices.{TrackingCommandService, UpdateTrackingStatusCommand}
import cargotracker.tracking.application.queryservices.TrackingQueryService
import cargotracker.tracking.domain.model.enums.TrackingStatus
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}

/** 認証ユーザー向け追跡照会画面（US18）。 */
@Singleton
class TrackingController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    queryService: TrackingQueryService,
    commandService: TrackingCommandService,
    bookingCommandService: BookingCommandService
) extends AbstractController(cc)
    with I18nSupport:

  private val updateStatusForm: Form[ManualStatusUpdateFormData] = Form(
    mapping(
      "status" -> nonEmptyText,
      "locationUnLocode" -> nonEmptyText(minLength = 5, maxLength = 5),
      "occurredAt" -> localDateTime("yyyy-MM-dd'T'HH:mm[:ss]")
    )(ManualStatusUpdateFormData.apply)(d => Some((d.status, d.locationUnLocode, d.occurredAt)))
  )

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

  /** 貨物状態の手動更新（US17 / IT6）。Tracker ロール想定。 */
  def updateStatus(trackingNumber: String): Action[AnyContent] = authenticated { implicit request =>
    val detailRoute = routes.TrackingController.detail(trackingNumber)
    updateStatusForm
      .bindFromRequest()
      .fold(
        _ => Redirect(detailRoute).flashing("error" -> "入力内容に誤りがあります"),
        data =>
          TrackingStatus.values.find(_.toString == data.status) match
            case None =>
              Redirect(detailRoute).flashing("error" -> s"未知の状態です: ${data.status}")
            case Some(status) =>
              val occurredInstant = data.occurredAt.atZone(ZoneId.systemDefault()).toInstant
              commandService.updateStatus(
                UpdateTrackingStatusCommand(trackingNumber, status, data.locationUnLocode, occurredInstant)
              ) match
                case Right(activity) =>
                  bookingCommandService.logManualStatusUpdate(
                    activity.bookingId.value,
                    trackingNumber,
                    status.toString,
                    data.locationUnLocode
                  )
                  Redirect(detailRoute).flashing("success" -> s"状態を $status に更新しました")
                case Left(msg) =>
                  Redirect(detailRoute).flashing("error" -> msg)
      )
  }

/** 状態手動更新フォームデータ。 */
final case class ManualStatusUpdateFormData(
    status: String,
    locationUnLocode: String,
    occurredAt: LocalDateTime
)
