package cargotracker.estimation.domain

import java.util.UUID

/** 見積の一意識別子。UUID ベース。 */
opaque type EstimateId = String

object EstimateId:
  /** 新しい EstimateId を発行する。 */
  def generate(): EstimateId = UUID.randomUUID().toString

  /** 永続化からの復元 */
  def unsafeFrom(raw: String): EstimateId = raw

  extension (id: EstimateId) def value: String = id
