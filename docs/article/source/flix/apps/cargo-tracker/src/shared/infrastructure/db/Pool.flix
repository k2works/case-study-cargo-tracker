///
/// コネクションプール（HikariCP）の設定と生成。
///
/// 本モジュールはインフラ層に閉じており、ドメイン層・アプリケーション層からは参照しない
/// （arch-lint 規約 1・2・3）。Java 相互運用はここに集約する。
///
/// **モジュール命名**: Flix 0.75.1 は同名のトップレベルモジュールを複数ファイルで宣言できないため、
/// 階層をドットではなく接頭辞で表現する（`SharedDbPool` = shared/infrastructure/db/Pool）。
/// レイヤ規約の検査はディレクトリパスで行う（arch-lint）。
///
mod SharedDbPool {
    import java.lang.Class
    import java.lang.Exception
    import java.sql.Connection
    import java.sql.PreparedStatement
    import java.sql.Statement
    import com.zaxxer.hikari.HikariConfig
    import com.zaxxer.hikari.HikariDataSource

    ///
    /// データソースの接続設定。
    ///
    /// `driverClass` を明示的に保持しているのは、Flix の実行時クラスローダでは
    /// JDBC ドライバの ServiceLoader 自動登録が機能せず、`Class.forName` による
    /// 明示的な登録が必要なためである（ADR-0001 参照）。
    ///
    pub enum DbConfig({
        jdbcUrl = String,
        driverClass = String,
        username = String,
        password = String,
        maximumPoolSize = Int32
    })

    /// 接続済みのデータソース。Java の型をシグネチャへ露出させないために enum で包む
    pub enum DataSource(HikariDataSource)

    /// H2（PostgreSQL 互換モード）の設定を作る。テストとローカル開発で使用する
    pub def h2Config(dbName: String): DbConfig = DbConfig.DbConfig({
        jdbcUrl = "jdbc:h2:mem:${dbName};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        driverClass = "org.h2.Driver",
        username = "sa",
        password = "",
        maximumPoolSize = 4
    })

    /// PostgreSQL の設定を作る。ステージング・本番で使用する
    pub def postgresConfig(jdbcUrl: String, username: String, password: String): DbConfig =
        DbConfig.DbConfig({
            jdbcUrl = jdbcUrl,
            driverClass = "org.postgresql.Driver",
            username = username,
            password = password,
            maximumPoolSize = 20
        })

    /// 設定からコネクションプールを生成する
    pub def create(config: DbConfig): DataSource \ IO =
        let DbConfig.DbConfig(c) = config;
        // ServiceLoader が機能しないため、ドライバを明示的に登録する
        discard Class.forName(c#driverClass);
        let hikari = new HikariConfig();
        hikari.setJdbcUrl(c#jdbcUrl);
        hikari.setUsername(c#username);
        hikari.setPassword(c#password);
        hikari.setMaximumPoolSize(c#maximumPoolSize);
        hikari.setDriverClassName(c#driverClass);
        DataSource.DataSource(new HikariDataSource(hikari))

    /// プールからコネクションを 1 本借りる。返却は呼び出し側の責務とする
    pub def borrow(ds: DataSource): Connection \ IO =
        let DataSource.DataSource(hikari) = ds;
        hikari.getConnection()

    /// 内部の HikariCP データソースを取り出す（Flyway 等の Java API へ渡すため）
    pub def unwrap(ds: DataSource): HikariDataSource =
        let DataSource.DataSource(hikari) = ds;
        hikari

    /// プールを閉じる
    pub def close(ds: DataSource): Unit \ IO =
        let DataSource.DataSource(hikari) = ds;
        hikari.close()

    ///
    /// コネクションを借りて関数を実行し、**成功・失敗にかかわらず必ず返却する**。
    ///
    /// 借用と返却を対にしないと、例外発生時にコネクションがプールへ戻らず枯渇する。
    /// JDBC を扱う箇所はすべて本関数（または `SharedDbTx.readOnly`）を経由すること。
    ///
    pub def withConnection(ds: DataSource, f: Connection -> a \ ef): a \ ef + IO =
        let conn = borrow(ds);
        try {
            let result = f(conn);
            conn.close();
            result
        } catch {
            case e: Exception =>
                conn.close();
                throw e
        }

    ///
    /// `Statement` を作って関数を実行し、成功・失敗にかかわらず必ず閉じる。
    ///
    pub def withStatement(conn: Connection, f: Statement -> a \ ef): a \ ef + IO =
        let stmt = conn.createStatement();
        try {
            let result = f(stmt);
            stmt.close();
            result
        } catch {
            case e: Exception =>
                stmt.close();
                throw e
        }

    ///
    /// `PreparedStatement` を作って関数を実行し、成功・失敗にかかわらず必ず閉じる。
    ///
    pub def withPreparedStatement(conn: Connection, sql: String,
                                  f: PreparedStatement -> a \ ef): a \ ef + IO =
        let stmt = conn.prepareStatement(sql);
        try {
            let result = f(stmt);
            stmt.close();
            result
        } catch {
            case e: Exception =>
                stmt.close();
                throw e
        }
}
