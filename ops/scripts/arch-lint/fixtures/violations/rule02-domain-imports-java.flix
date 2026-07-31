// 規約 2 違反: domain 層が java を import する
// パス: src/tracking/domain/model/Bad.flix 相当
mod TrackingBadDomainJava {
    import java.sql.Connection

    pub def bad(): Unit = ()
}
