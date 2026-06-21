package cargotracker.booking.domain.model.aggregates

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.valueobjects.{BookingId, BookingStatus, CargoSpec, RouteSpecification}
import cargotracker.shared.domain.ShipperId

/** 貨物予約（Booking Context の集約ルート）。
  *
  *   - 業務キー: `BookingId`（`BK-NNNNNN`）
  *   - 状態遷移は `BookingStatus` の規約に従う（domain-model.md ビジネスルール 4）
  *   - 旅程・追跡情報は本 IT では未実装（IT4・IT5 で追加）
  */
final case class Cargo private (
    bookingId: BookingId,
    shipperId: ShipperId,
    routeSpecification: RouteSpecification,
    cargoSpec: CargoSpec,
    status: BookingStatus
):

  /** 経路設計者への引き渡し（US06 / `AssignToRoutingCommand`）。
    *
    *   - `Preliminary` 以外からは呼び出せない（`InvalidStatusTransition`）
    *   - 成功時は `RouteProposed` 状態の新インスタンスを返す
    */
  def assignToRouting(): Either[Cargo.Error, Cargo] =
    if status.canTransitionTo(BookingStatus.RouteProposed) then Right(copy(status = BookingStatus.RouteProposed))
    else Left(Cargo.InvalidStatusTransition(status, BookingStatus.RouteProposed))

object Cargo:

  sealed trait Error
  case object UnknownShipper extends Error
  final case class InvalidStatusTransition(from: BookingStatus, to: BookingStatus) extends Error

  /** 新規予約を生成する。
    *
    * ShipperExistenceChecker で荷主存在を確認し、初期状態は `Preliminary`。
    */
  def book(
      bookingId: BookingId,
      shipperId: ShipperId,
      routeSpecification: RouteSpecification,
      cargoSpec: CargoSpec,
      shipperChecker: ShipperExistenceChecker
  ): Either[Error, Cargo] =
    if !shipperChecker.exists(shipperId) then Left(UnknownShipper)
    else
      Right(
        new Cargo(
          bookingId = bookingId,
          shipperId = shipperId,
          routeSpecification = routeSpecification,
          cargoSpec = cargoSpec,
          status = BookingStatus.Preliminary
        )
      )

  /** 永続化からの復元 */
  def reconstruct(
      bookingId: BookingId,
      shipperId: ShipperId,
      routeSpecification: RouteSpecification,
      cargoSpec: CargoSpec,
      status: BookingStatus
  ): Cargo =
    new Cargo(bookingId, shipperId, routeSpecification, cargoSpec, status)
