///
/// Flyway による DB マイグレーション。
///
/// テストと本番で同一のマイグレーションスクリプト（resources/db/migration）を適用する。
/// テスト専用の DDL は作らない（docs/design/test_strategy.md 3.2.1）。
///
mod SharedDbMigration {
    import java.lang.Class
    import org.flywaydb.core.Flyway

    use SharedDbPool.DbConfig

    ///
    /// マイグレーションスクリプトの探索場所。
    ///
    /// `resources/` の内容は `flix build-fatjar` では JAR のルートへ展開されるため
    /// 本番では `classpath:` で見つかるが、`flix test` / `flix run` の実行時は
    /// クラスパスに載らない。そのため両方を指定し、存在する方から読み込ませる。
    ///
    def classpathLocation(): String = "classpath:db/migration"
    def filesystemLocation(): String = "filesystem:resources/db/migration"

    ///
    /// マイグレーションを適用し、適用したスクリプト数を返す。
    /// すでに最新の場合は 0 を返す（冪等）。
    ///
    /// コネクションプールではなく接続設定を受け取るのは、Flyway が
    /// 起動時に 1 度だけ実行される処理であり、プールを共有する必要がないためである。
    ///
    pub def migrate(config: DbConfig): Int32 \ IO =
        let DbConfig.DbConfig(c) = config;
        // Flix の実行時クラスローダでは Flyway が JDBC ドライバを解決できないため、
        // ドライバクラスをロードしたクラスローダを明示的に渡す（ADR-0001 の制約）
        let driverClass = Class.forName(c#driverClass);
        let loader = driverClass.getClassLoader();
        region rc {
            let locations = Array#{classpathLocation(), filesystemLocation()} @ rc;
            let configured = Flyway.configure(loader);
            let withDs = configured.dataSource(c#jdbcUrl, c#username, c#password);
            let withLoc = withDs.locations(locations);
            let flyway = withLoc.load();
            let result = flyway.migrate();
            result.migrationsExecuted
        }
}
