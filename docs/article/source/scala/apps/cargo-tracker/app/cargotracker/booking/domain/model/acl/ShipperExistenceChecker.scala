package cargotracker.booking.domain.model.acl

import cargotracker.shared.domain.ShipperId
import cargotracker.shipper.domain.model.aggregates.Shipper
import cargotracker.shipper.domain.model.repositories.ShipperRepository

/** Shipper Context への ACL ポート（domain-model.md 準拠）。
  *
  * Booking Context は Shipper Context のエンティティに直接依存せず、本ポート経由で 存在確認のみを行う。実装は infrastructure 層で `ShipperRepository` をラップする。
  */
trait ShipperExistenceChecker:

  /** 指定 ShipperId の荷主が存在するか確認する。 */
  def exists(shipperId: ShipperId): Boolean
