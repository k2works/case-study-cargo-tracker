package cargotracker.shipper.domain

import cargotracker.shared.domain.ShipperId

/** 荷主集約の永続化ポート。 */
trait ShipperRepository:

  /** ShipperId で荷主を取得する。 */
  def findById(shipperId: ShipperId): Option[Shipper]

  /** メールアドレスで荷主を取得する（重複チェック・US02 受入条件）。 */
  def findByEmail(email: String): Option[Shipper]

  /** 全荷主を取得する。 */
  def findAll(): Seq[Shipper]

  /** 荷主を保存する。 */
  def save(shipper: Shipper): Unit

  /** 次に発行する ShipperId（業務キー）を採番する。 */
  def nextIdentity(): ShipperId
