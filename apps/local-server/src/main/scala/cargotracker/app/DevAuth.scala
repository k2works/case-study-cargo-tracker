package cargotracker.app

import cargotracker.sharedkernel.{Principal, Role, Ulid}

/** 開発用の認証スタブ。ヘッダー `X-Debug-User-Id`（ULID）と `X-Debug-Role`（ロール名）で利用者を渡す。 **本番の認証ではない**（認証の実装は Bolt
  * 2。R-02）。設定で明示的に有効にしたときだけ使え、既定は無効。
  */
object DevAuth:
  def principal(userId: Option[String], role: Option[String]): Option[Principal] =
    for
      u <- userId
      r <- role
      id <- Ulid.parse(u).toOption
      ro <- Role.values.find(_.toString == r)
    yield Principal(id, ro)
