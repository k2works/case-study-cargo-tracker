// 適法: domain 層が共有カーネル（shared）を参照する
// パス: src/tracking/domain/port/Ok.flix 相当
mod TrackingOkDomain {
    use SharedLocation.Location

    pub def ok(): Unit = ()
}
