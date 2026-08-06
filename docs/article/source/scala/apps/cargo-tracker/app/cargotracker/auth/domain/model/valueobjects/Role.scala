package cargotracker.auth.domain.model.valueobjects

/** システムユーザーの役割（ロール）。
  *
  * 各ロールは UI ナビゲーション・認可・業務ログの分離に利用する。
  */
enum Role:
  case Sales
  case RouteDesigner
  case Tracker
  case Settlement
  case MasterAdmin

object Role:

  /** 永続化からの読み戻し用に文字列名と Role を相互変換する。
    *
    * 未知の名前は [[scala.None]] を返す。
    */
  def fromName(name: String): Option[Role] =
    values.find(_.toString.equalsIgnoreCase(name))
