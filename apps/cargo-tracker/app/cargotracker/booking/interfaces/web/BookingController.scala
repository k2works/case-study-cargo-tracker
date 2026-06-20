package cargotracker.booking.interfaces.web

import cargotracker.booking.domain.{
  BookingId,
  Cargo,
  CargoRepository,
  CargoSpec,
  HazardousDeclaration,
  RouteSpecification,
  ShipperExistenceChecker
}
import cargotracker.shared.domain.{CargoType, Location, ShipperId, Weight}
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
    hazardousProperName: Option[String]
)

@Singleton
class BookingController @Inject() (
    cc: ControllerComponents,
    repository: CargoRepository,
    shipperChecker: ShipperExistenceChecker
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
      "hazardousProperName" -> optional(text(maxLength = 200))
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
          d.hazardousProperName
        )
      )
    )
  )

  def list(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.booking.list(repository.findAll()))
  }

  def newForm(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.booking.form(bookingForm, errorMessage = None))
  }

  def create(): Action[AnyContent] = Action { implicit request =>
    bookingForm
      .bindFromRequest()
      .fold(
        formWithErrors =>
          BadRequest(
            views.html.booking
              .form(formWithErrors, errorMessage = Some("入力内容を確認してください"))
          ),
        data => persistBooking(data)
      )
  }

  def detail(bookingId: String): Action[AnyContent] = Action { implicit request =>
    repository.findById(BookingId.unsafeFrom(bookingId)) match
      case Some(cargo) => Ok(views.html.booking.detail(cargo))
      case None => NotFound("予約が見つかりません")
  }

  private def persistBooking(data: BookingFormData)(implicit
      request: RequestHeader
  ): Result =
    val result = for
      origin <- Location(data.origin).left.map(_ => "出発地の UnLocode 形式が不正です")
      destination <- Location(data.destination).left
        .map(_ => "目的地の UnLocode 形式が不正です")
      cargoType <- CargoType
        .fromName(data.cargoType)
        .toRight("貨物種別が不正です")
      weight <- Weight(data.weightKg).left.map(_ => "重量が不正です")
      routeSpec <- RouteSpecification(origin, destination, data.arrivalDeadline).left
        .map(_ => "出発地と目的地が同じです")
      hazardous = for
        hc <- data.hazardousClass.filter(_.nonEmpty)
        un <- data.hazardousUnNumber.filter(_.nonEmpty)
        psn <- data.hazardousProperName.filter(_.nonEmpty)
      yield HazardousDeclaration(hc, un, psn)
      spec = CargoSpec(
        cargoType = cargoType,
        weight = weight,
        description = data.description.filter(_.nonEmpty),
        quantity = data.quantity,
        hazardous = hazardous
      )
      cargo <- Cargo
        .book(
          repository.nextIdentity(),
          ShipperId.unsafeFrom(data.shipperCode),
          routeSpec,
          spec,
          shipperChecker
        )
        .left
        .map(_ => s"荷主 ${data.shipperCode} が見つかりません")
    yield cargo

    result match
      case Right(cargo) =>
        repository.save(cargo)
        Redirect(
          cargotracker.booking.interfaces.web.routes.BookingController
            .detail(cargo.bookingId.value)
        ).flashing(
          "success" -> s"貨物予約 ${cargo.bookingId.value} を登録しました"
        )
      case Left(msg) =>
        BadRequest(
          views.html.booking.form(bookingForm, errorMessage = Some(msg))
        )
