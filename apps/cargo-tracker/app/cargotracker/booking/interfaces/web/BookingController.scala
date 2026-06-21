package cargotracker.booking.interfaces.web

import cargotracker.auth.interfaces.web.AuthenticatedAction
import cargotracker.booking.application.commandservices.{BookCargoCommand, BookingCommandService}
import cargotracker.booking.application.queryservices.BookingQueryService
import play.api.data.Form
import play.api.data.Forms.*
import play.api.i18n.I18nSupport
import play.api.mvc.*

import java.time.LocalDate
import javax.inject.{Inject, Singleton}

final case class BookingFormData(
    shipperCode: String,
    origin: String,
    destination: String,
    arrivalDeadline: LocalDate,
    cargoType: String,
    weightKg: Long,
    description: Option[String],
    quantity: Option[Int],
    hazardousClass: Option[String],
    hazardousUnNumber: Option[String],
    hazardousProperName: Option[String],
    refrigerationMinTemp: Option[Int],
    refrigerationMaxTemp: Option[Int],
    refrigerationUnit: Option[String]
)

@Singleton
class BookingController @Inject() (
    cc: ControllerComponents,
    authenticated: AuthenticatedAction,
    commandService: BookingCommandService,
    queryService: BookingQueryService
) extends AbstractController(cc)
    with I18nSupport:

  private val bookingForm: Form[BookingFormData] = Form(
    mapping(
      "shipperCode" -> nonEmptyText(maxLength = 20),
      "origin" -> nonEmptyText(minLength = 5, maxLength = 5),
      "destination" -> nonEmptyText(minLength = 5, maxLength = 5),
      "arrivalDeadline" -> localDate,
      "cargoType" -> nonEmptyText,
      "weightKg" -> longNumber(min = 1, max = 999_999_999L),
      "description" -> optional(text(maxLength = 500)),
      "quantity" -> optional(number(min = 1)),
      "hazardousClass" -> optional(text(maxLength = 50)),
      "hazardousUnNumber" -> optional(text(maxLength = 20)),
      "hazardousProperName" -> optional(text(maxLength = 200)),
      "refrigerationMinTemp" -> optional(number(min = -273, max = 100)),
      "refrigerationMaxTemp" -> optional(number(min = -273, max = 100)),
      "refrigerationUnit" -> optional(text(maxLength = 10))
    )(BookingFormData.apply)(d =>
      Some(
        (
          d.shipperCode,
          d.origin,
          d.destination,
          d.arrivalDeadline,
          d.cargoType,
          d.weightKg,
          d.description,
          d.quantity,
          d.hazardousClass,
          d.hazardousUnNumber,
          d.hazardousProperName,
          d.refrigerationMinTemp,
          d.refrigerationMaxTemp,
          d.refrigerationUnit
        )
      )
    )
  )

  def list(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.booking.list(queryService.findAll()))
  }

  def newForm(): Action[AnyContent] = authenticated { implicit request =>
    Ok(views.html.booking.formPage(bookingForm, errorMessage = None))
  }

  def create(): Action[AnyContent] = authenticated { implicit request =>
    bookingForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.booking
              .formPage(formWithErrors, errorMessage = Some("入力内容を確認してください"))
          ),
        data =>
          commandService.book(
            BookCargoCommand(
              shipperCode = data.shipperCode,
              origin = data.origin,
              destination = data.destination,
              arrivalDeadline = data.arrivalDeadline,
              cargoType = data.cargoType,
              weightKg = data.weightKg,
              description = data.description,
              quantity = data.quantity,
              hazardousClass = data.hazardousClass,
              hazardousUnNumber = data.hazardousUnNumber,
              hazardousProperName = data.hazardousProperName,
              refrigerationMinTemp = data.refrigerationMinTemp,
              refrigerationMaxTemp = data.refrigerationMaxTemp,
              refrigerationUnit = data.refrigerationUnit
            )
          ) match
            case Right(cargo) =>
              Redirect(
                cargotracker.booking.interfaces.web.routes.BookingController
                  .detail(cargo.bookingId.value)
              ).flashing(
                "success" -> s"貨物予約 ${cargo.bookingId.value} を登録しました"
              )
            case Left(msg) =>
              BadRequest(
                views.html.booking.formPage(bookingForm, errorMessage = Some(msg))
              )
      )
  }

  def detail(bookingId: String): Action[AnyContent] = authenticated { implicit request =>
    queryService.findById(bookingId) match
      case Some(cargo) => Ok(views.html.booking.detail(cargo))
      case None => NotFound("予約が見つかりません")
  }
