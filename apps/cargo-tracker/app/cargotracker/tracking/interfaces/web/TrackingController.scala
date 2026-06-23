package cargotracker.tracking.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
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
      "occurredAt" -> localDateTime("yyyy-MM-dd'T'HH:mm[:ss]"),
      "reason" -> nonEmptyText(minLength = 1, maxLength = 500)
    )(ManualStatusUpdateFormData.apply)(d => Some((d.status, d.locationUnLocode, d.occurredAt, d.reason)))
  )

  private val ManualUpdateAllowedRoles: Set[Role] = Set(Role.Tracker, Role.MasterAdmin)

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

  /** 追跡詳細（30 秒 htmx ポーリングでタイムライン更新）。手動更新ボタンは Tracker / MasterAdmin のみ表示。 */
  def detail(trackingNumber: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findByTrackingNumber(trackingNumber) match
      case Some(view) =>
        val canManualUpdate = request.roles.exists(ManualUpdateAllowedRoles.contains)
        Ok(views.html.tracking.detail(view, canManualUpdate))
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

  /** 貨物状態の手動更新（US17 / IT6 + IT7 0.13）。`Tracker` または `MasterAdmin` 限定、更新理由必須。 */
  def updateStatus(trackingNumber: String): Action[AnyContent] = authenticated { implicit request =>
    val detailRoute = routes.TrackingController.detail(trackingNumber)
    if !request.roles.exists(ManualUpdateAllowedRoles.contains) then
      Redirect(detailRoute).flashing("error" -> "状態を手動更新する権限がありません")
    else
      updateStatusForm
        .bindFromRequest()
        .fold(
          _ => Redirect(detailRoute).flashing("error" -> "入力内容に誤りがあります（更新理由は必須）"),
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
                      data.locationUnLocode,
                      data.reason
                    )
                    Redirect(detailRoute).flashing("success" -> s"状態を $status に更新しました")
                  case Left(msg) =>
                    Redirect(detailRoute).flashing("error" -> msg)
        )
  }

/** 状態手動更新フォームデータ（IT7 0.13 で `reason` 追加）。 */
final case class ManualStatusUpdateFormData(
    status: String,
    locationUnLocode: String,
    occurredAt: LocalDateTime,
    reason: String
)
