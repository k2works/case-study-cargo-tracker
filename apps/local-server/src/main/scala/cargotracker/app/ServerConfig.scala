package cargotracker.app

/** 実行物の設定。値（接続情報など）はリポジトリに置かず、実行時の環境変数から与える（R-10）。 */
final case class ServerConfig(
    dbUrl: String,
    dbUser: String,
    dbPassword: String,
    httpHost: String,
    httpPort: Int,
    /** 開発用の認証スタブ（ヘッダーで利用者とロールを渡す）。**既定は無効**。認証の実装は Bolt 2。 */
    devAuthEnabled: Boolean
)

object ServerConfig:
  /** 環境変数から読む。必須の値が無ければ、何が足りないかを示して失敗する。 */
  def fromEnv(env: Map[String, String] = sys.env): Either[String, ServerConfig] =
    def required(name: String) = env.get(name).filter(_.nonEmpty).toRight(s"環境変数 $name が必要")
    for
      url <- required("CARGO_TRACKER_DB_URL")
      user <- required("CARGO_TRACKER_DB_USER")
      pw <- required("CARGO_TRACKER_DB_PASSWORD")
      port <- env
        .get("CARGO_TRACKER_HTTP_PORT")
        .fold[Either[String, Int]](Right(8080))(p =>
          p.toIntOption
            .filter(n => n >= 0 && n <= 65535)
            .toRight("CARGO_TRACKER_HTTP_PORT は 0〜65535")
        )
    yield ServerConfig(
      url,
      user,
      pw,
      env.getOrElse("CARGO_TRACKER_HTTP_HOST", "127.0.0.1"),
      port,
      env.get("CARGO_TRACKER_DEV_AUTH").contains("true")
    )
