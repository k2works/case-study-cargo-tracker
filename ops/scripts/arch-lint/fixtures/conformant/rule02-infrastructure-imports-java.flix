// 適法: infrastructure 層が java を import する（Java 相互運用はインフラ層に閉じる）
// パス: src/shared/infrastructure/db/Ok.flix 相当
mod SharedOkInfraJava {
    import java.sql.Connection
    import java.lang.Class

    pub def ok(): Unit = ()
}
