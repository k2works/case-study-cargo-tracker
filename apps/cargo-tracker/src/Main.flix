import java.lang.System

///
/// アプリケーションのエントリポイント。
///
/// 起動時にマイグレーションを 1 度だけ適用し、HTTP サーバを起動する。
/// 効果ハンドラの結線は合成ルート（AppComposition）に集約されている。
///
def main(): Unit \ IO =
    let config = appConfig();
    let applied = SharedDbMigration.migrate(config);
    println("マイグレーション適用: ${applied} 件");
    let ds = SharedDbPool.create(config);
    let portNumber = appPort();
    let server = AppComposition.startServer(ds, portNumber);
    println("cargo-tracker を起動しました: http://localhost:${SharedHttpServer.port(server)}");
    println("公開追跡: http://localhost:${SharedHttpServer.port(server)}/public/tracking/{trackingNumber}")

/// 環境変数を取得する（Java 相互運用）
def envVar(name: String): Option[String] \ IO =
    let value = System.getenv(name);
    if (Object.isNull(value)) None else Some(value)

/// 接続設定。環境変数 DATABASE_URL があれば PostgreSQL、なければ H2 を使う
def appConfig(): SharedDbPool.DbConfig \ IO =
    match envVar("DATABASE_URL") {
        case Some(url) =>
            let user = envVar("DATABASE_USER") |> Option.getWithDefault("cargotracker");
            let password = envVar("DATABASE_PASSWORD") |> Option.getWithDefault("");
            SharedDbPool.postgresConfig(url, user, password)
        case None =>
            SharedDbPool.h2Config("cargotracker")
    }

/// 待ち受けポート。環境変数 PORT があればそれを使う
def appPort(): Int32 \ IO =
    envVar("PORT")
        |> Option.flatMap(Int32.fromString)
        |> Option.getWithDefault(8080)
