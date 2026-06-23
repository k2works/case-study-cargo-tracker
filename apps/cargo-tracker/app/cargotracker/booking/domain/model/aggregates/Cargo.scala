package cargotracker.booking.domain.model.aggregates

import cargotracker.booking.domain.model.acl.ShipperExistenceChecker
import cargotracker.booking.domain.model.valueobjects.{
  BookingId,
  BookingStatus,
  BookingTrackingNumber,
  CargoSpec,
  Itinerary,
  RouteSpecification
}
import cargotracker.shared.domain.ShipperId

/** 貨物予約（Booking Context の集約ルート）。
  *
  *   - 業務キー: `BookingId`（`BK-NNNNNN`）
  *   - 状態遷移は `BookingStatus` の規約に従う（domain-model.md ビジネスルール 4）
  *   - 楽観ロック: `version: Int` を持ち、リポジトリ UPDATE は `WHERE id=? AND version=?` で 比較更新する（IT3 タスク 0.2 で活性化）
  */
final case class Cargo private (
    bookingId: BookingId,
    shipperId: ShipperId,
    routeSpecification: RouteSpecification,
    cargoSpec: CargoSpec,
    status: BookingStatus,
    version: Int,
    itinerary: Option[Itinerary] = None,
    trackingNumber: Option[BookingTrackingNumber] = None
):

  /** 経路設計者への引き渡し（US06 / `AssignToRoutingCommand`）。
    *
    *   - `Preliminary` 以外からは呼び出せない（`InvalidStatusTransition`）
    *   - 成功時は `RouteProposed` 状態の新インスタンスを返す
    */
  def assignToRouting(): Either[Cargo.Error, Cargo] =
    if status.canTransitionTo(BookingStatus.RouteProposed) then Right(copy(status = BookingStatus.RouteProposed))
    else Left(Cargo.InvalidStatusTransition(status, BookingStatus.RouteProposed))

  /** 経路情報を予約に紐付ける（US11 / `AssignItineraryCommand`）。
    *
    *   - `RouteProposed` 以外からは呼び出せない（`InvalidStatusTransition`）
    *   - 成功時は `RouteAssigned` 状態の新インスタンスを返し、`itinerary` を保持する
    */
  def assignItinerary(itinerary: Itinerary): Either[Cargo.Error, Cargo] =
    if status.canTransitionTo(BookingStatus.RouteAssigned) then
      Right(copy(status = BookingStatus.RouteAssigned, itinerary = Some(itinerary)))
    else Left(Cargo.InvalidStatusTransition(status, BookingStatus.RouteAssigned))

  /** 予約を確定する（US13 / `ConfirmBookingCommand`）。
    *
    *   - `RouteAssigned` 以外からは呼び出せない
    *   - 成功時は `Confirmed` 状態の新インスタンスを返す
    */
  def confirm(): Either[Cargo.Error, Cargo] =
    if status.canTransitionTo(BookingStatus.Confirmed) then Right(copy(status = BookingStatus.Confirmed))
    else Left(Cargo.InvalidStatusTransition(status, BookingStatus.Confirmed))

  /** 経路再設計に戻す（US13 / `ReproposeRouteCommand`）。
    *
    *   - `RouteAssigned` のみから可能
    *   - 成功時は `RouteProposed` 状態に戻し、紐付け済 `itinerary` を破棄する
    */
  def reproposeRoute(): Either[Cargo.Error, Cargo] =
    if status == BookingStatus.RouteAssigned then Right(copy(status = BookingStatus.RouteProposed, itinerary = None))
    else Left(Cargo.InvalidStatusTransition(status, BookingStatus.RouteProposed))

  /** 予約をキャンセルする（US13 / `CancelBookingCommand`）。
    *
    *   - 確定前（Preliminary / RouteProposed / RouteAssigned / Confirmed）のみ可能
    *   - 成功時は `Cancelled` 状態の新インスタンスを返す
    */
  def cancel(): Either[Cargo.Error, Cargo] =
    if status.canTransitionTo(BookingStatus.Cancelled) then Right(copy(status = BookingStatus.Cancelled))
    else Left(Cargo.InvalidStatusTransition(status, BookingStatus.Cancelled))

  /** 追跡番号を発行する（US14 / `AssignTrackingNumberCommand`）。
    *
    *   - `Confirmed` 以外からは呼び出せない
    *   - 既に `trackingNumber` が設定済みの場合は冪等成功（既存値を保持して `TrackingIssued` を返す）
    *   - 成功時は `TrackingIssued` 状態に遷移し、`trackingNumber` を保持
    */
  def issueTracking(trackingNumber: BookingTrackingNumber): Either[Cargo.Error, Cargo] =
    this.trackingNumber match
      case Some(_) =>
        // 冪等: 既に発行済みの場合は現状を返す（再採番禁止）
        Right(this)
      case None =>
        if status.canTransitionTo(BookingStatus.TrackingIssued) then
          Right(copy(status = BookingStatus.TrackingIssued, trackingNumber = Some(trackingNumber)))
        else Left(Cargo.InvalidStatusTransition(status, BookingStatus.TrackingIssued))

  /** 文字列から `BookingTrackingNumber` を検証して `issueTracking` を呼び出す便宜メソッド（H2 補助）。 */
  def issueTrackingByRaw(trackingNumberRaw: String): Either[Cargo.Error, Cargo] =
    BookingTrackingNumber(trackingNumberRaw) match
      case Left(_) => Left(Cargo.InvalidTrackingNumberFormat(trackingNumberRaw))
      case Right(tn) => issueTracking(tn)

  /** 引取完了 (US16 / IT6) で `Delivered` に遷移する。
    *
    *   - `InTransit` または `TrackingIssued` から遷移可能 (Claim 記録時に呼出)
    *   - 既に `Delivered` 以降の場合は冪等成功 (現状を返す)
    */
  def deliver(): Either[Cargo.Error, Cargo] =
    status match
      case BookingStatus.Delivered | BookingStatus.Settled => Right(this)
      case _ =>
        if status.canTransitionTo(BookingStatus.Delivered) then Right(copy(status = BookingStatus.Delivered))
        else Left(Cargo.InvalidStatusTransition(status, BookingStatus.Delivered))

object Cargo:

  sealed trait Error
  case object UnknownShipper extends Error
  final case class InvalidStatusTransition(from: BookingStatus, to: BookingStatus) extends Error
  final case class InvalidTrackingNumberFormat(raw: String) extends Error

  /** 新規予約を生成する。
    *
    * ShipperExistenceChecker で荷主存在を確認し、初期状態は `Preliminary`、`version = 0`。
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
          status = BookingStatus.Preliminary,
          version = 0
        )
      )

  /** 永続化からの復元（DB の `version` カラムを保持） */
  def reconstruct(
      bookingId: BookingId,
      shipperId: ShipperId,
      routeSpecification: RouteSpecification,
      cargoSpec: CargoSpec,
      status: BookingStatus,
      version: Int = 0,
      itinerary: Option[Itinerary] = None,
      trackingNumber: Option[String] = None
  ): Cargo =
    new Cargo(
      bookingId,
      shipperId,
      routeSpecification,
      cargoSpec,
      status,
      version,
      itinerary,
      trackingNumber.map(BookingTrackingNumber.unsafeFrom)
    )
