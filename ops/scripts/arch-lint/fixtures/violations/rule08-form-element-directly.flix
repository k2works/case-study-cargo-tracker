// 規約 8 違反: Components.form を経由せず form を直接構築している
// パス: src/tracking/interfaces/web/Bad.flix 相当
mod TrackingBadForm {
    pub def bad(): Html =
        element("form", attr("action", "/search") :: Nil, Nil)
}
