///
/// 規約 13 の正例: 向きを宣言している。
///
/// **負例との違いは 1 行**（`aclTarget` の有無）である。
///
mod BookingSomethingAdapter {
    pub def aclTarget(): String = "booking:write"

    pub type alias Requires = CargoRepo

    pub def withSomething(f: Unit -> a \ ef + Something):
            a \ (ef - Something) + Requires =
        run { f() } with handler Something {
            def doIt(k) = k()
        }
}
