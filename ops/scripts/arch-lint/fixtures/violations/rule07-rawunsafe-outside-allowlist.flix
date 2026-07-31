// 規約 7 違反: 許可リスト外で RawUnsafe を使っている
// パス: src/tracking/interfaces/web/Bad.flix 相当
mod TrackingBadRawUnsafe {
    pub def bad(userInput: String): Html =
        Html.RawUnsafe(userInput)
}
