///
/// 環境構築の検証用スモークモジュール。
///
/// アプリケーション開発環境が「設計が前提とする 2 つの機構」を実際に満たすことを確認する。
///   1. 代数的効果とハンドラによる出力ポートの注入（ヘキサゴナルの中核）
///   2. Java 相互運用による JDBC 接続（永続化アダプタの中核）
///
/// 本モジュールは実装が始まったら削除してよい。
///
mod Bootstrap {
    import java.lang.Class
    import java.sql.DriverManager

    /// 出力ポートの最小例。実装は知らず、要求するだけ。
    pub eff CargoRepo {
        def findById(id: String): Option[String]
        def store(value: String): Unit
    }

    /// アプリケーションサービスの最小例。必要な効果がシグネチャに現れる。
    pub def findCargo(id: String): String \ CargoRepo =
        match CargoRepo.findById(id) {
            case Some(v) => CargoRepo.store("stored:" + v); v
            case None    => "not-found"
        }

    /// テスト用アダプタ（インメモリ実装のハンドラ）。モックライブラリの代替。
    pub def withInMemoryCargoRepo(f: Unit -> a \ ef + CargoRepo): a \ ef - CargoRepo =
        run f() with handler CargoRepo {
            def findById(id, k) = k(if (id == "C1") Some("cargo-1") else None)
            def store(_v, k)    = k()
        }

    /// JDBC 疎通確認。
    ///
    /// 注意: Flix の実行時クラスローダでは JDBC ドライバの ServiceLoader 自動登録が
    /// 機能しないため、`Class.forName` による明示的なドライバ登録が必要である。
    pub def probeDatabase(jdbcUrl: String, driverClass: String): String \ IO =
        discard Class.forName(driverClass);
        let conn = DriverManager.getConnection(jdbcUrl);
        let stmt = conn.createStatement();
        let rs = stmt.executeQuery("SELECT 1");
        if (rs.next()) "ok" else "empty"
}
