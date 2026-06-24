package cargotracker.tracking.interfaces.web

import cargotracker.auth.domain.model.valueobjects.Role
import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.commandservices.BookingCommandService
import cargotracker.tracking.application.commandservices.{
  RecordExceptionCommand,
  ResolveExceptionCommand,
  TrackingCommandService,
  UpdateTrackingStatusCommand
}
import cargotracker.tracking.application.queryservices.TrackingQueryService
import cargotracker.tracking.domain.model.enums.{ExceptionType, TrackingStatus}
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.{Clock, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}

/** 認証ユーザー向け追跡照会画面（US18）。 */
@Singleton
class TrackingController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    queryService: TrackingQueryService,
    commandService: TrackingCommandService,
    bookingCommandService: BookingCommandService,
    clock: Clock
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

  private val recordExceptionForm: Form[RecordExceptionFormData] = Form(
    mapping(
      "exceptionType" -> nonEmptyText,
      "locationUnLocode" -> nonEmptyText(minLength = 5, maxLength = 5),
      "occurredAt" -> localDateTime("yyyy-MM-dd'T'HH:mm[:ss]"),
      "description" -> optional(text(maxLength = 500))
    )(RecordExceptionFormData.apply)(d => Some((d.exceptionType, d.locationUnLocode, d.occurredAt, d.description)))
  )

  private val resolveExceptionForm: Form[ResolveExceptionFormData] = Form(
    mapping(
      "resolutionNotes" -> nonEmptyText(minLength = 1, maxLength = 500)
    )(ResolveExceptionFormData.apply)(d => Some(d.resolutionNotes))
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

  /** 追跡例外を記録する（US19/US20）。Tracker/MasterAdmin 限定。 */
  def recordException(trackingNumber: String): Action[AnyContent] = authenticated { implicit request =>
    val detailRoute = routes.TrackingController.detail(trackingNumber)
    if !request.roles.exists(ManualUpdateAllowedRoles.contains) then
      Redirect(detailRoute).flashing("error" -> "例外を記録する権限がありません")
    else
      recordExceptionForm
        .bindFromRequest()
        .fold(
          _ => Redirect(detailRoute).flashing("error" -> "入力内容に誤りがあります"),
          data =>
            ExceptionType.fromName(data.exceptionType) match
              case None =>
                Redirect(detailRoute).flashing("error" -> s"未知の例外種別です: ${data.exceptionType}")
              case Some(et) =>
                val occurredInstant = data.occurredAt.atZone(ZoneId.systemDefault()).toInstant
                commandService.recordException(
                  RecordExceptionCommand(trackingNumber, et, data.locationUnLocode, occurredInstant, data.description)
                ) match
                  case Right(activity) =>
                    // 通知ログ: Delay→DelayNotified、Damage→DamageReported、Lost→LostEscalated
                    et match
                      case ExceptionType.Delay =>
                        bookingCommandService.logDelayNotification(
                          activity.bookingId.value,
                          trackingNumber,
                          newEstimatedArrival = "未確定",
                          responsePlan = data.description.getOrElse(""),
                          reason = data.description.getOrElse("")
                        )
                      case ExceptionType.Damage =>
                        bookingCommandService.logDamageReport(
                          activity.bookingId.value,
                          trackingNumber,
                          data.locationUnLocode,
                          data.description.getOrElse("")
                        )
                      case ExceptionType.Lost =>
                        bookingCommandService.escalateLost(
                          activity.bookingId.value,
                          trackingNumber,
                          data.locationUnLocode,
                          data.description.getOrElse("")
                        )
                      case ExceptionType.CustomsHold => ()
                    Redirect(detailRoute).flashing("success" -> s"例外 ($et) を記録しました")
                  case Left(msg) =>
                    Redirect(detailRoute).flashing("error" -> msg)
        )
  }

  /** 追跡例外の対応報告。Tracker/MasterAdmin 限定。 */
  def resolveException(trackingNumber: String, index: Int): Action[AnyContent] = authenticated { implicit request =>
    val detailRoute = routes.TrackingController.detail(trackingNumber)
    if !request.roles.exists(ManualUpdateAllowedRoles.contains) then
      Redirect(detailRoute).flashing("error" -> "対応報告する権限がありません")
    else
      resolveExceptionForm
        .bindFromRequest()
        .fold(
          _ => Redirect(detailRoute).flashing("error" -> "入力内容に誤りがあります（対応内容は必須）"),
          data =>
            commandService.resolveException(
              ResolveExceptionCommand(trackingNumber, index, clock.instant(), data.resolutionNotes)
            ) match
              case Right(activity) =>
                val resolved = activity.exceptions(index)
                bookingCommandService.logExceptionResponse(
                  activity.bookingId.value,
                  trackingNumber,
                  resolved.exceptionType.toString,
                  data.resolutionNotes
                )
                Redirect(detailRoute).flashing("success" -> s"例外 (#$index) の対応を報告しました")
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

/** 追跡例外記録フォームデータ（IT7 US19/US20）。 */
final case class RecordExceptionFormData(
    exceptionType: String,
    locationUnLocode: String,
    occurredAt: LocalDateTime,
    description: Option[String]
)

/** 追跡例外対応報告フォームデータ（IT7 US19/US20）。 */
final case class ResolveExceptionFormData(resolutionNotes: String)
