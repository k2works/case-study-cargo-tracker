///
/// 規約 13 の負例: ACL アダプタが向きを宣言していない。
///
/// **実コードの形で書く**。`Requires` の型別名は規約 11 を通すために要るが、
/// **向きの宣言が無いと ADR-0014 決定 4 の条件を数えられない**——
/// 「単一 BC へ向かう書き込みが 3 本を超えたか」が測れなくなる。
///
mod BookingSomethingAdapter {
    pub type alias Requires = CargoRepo

    pub def withSomething(f: Unit -> a \ ef + Something):
            a \ (ef - Something) + Requires =
        run { f() } with handler Something {
            def doIt(k) = k()
        }
}
