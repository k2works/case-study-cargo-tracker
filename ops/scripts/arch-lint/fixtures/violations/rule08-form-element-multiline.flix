/// 負例: element( と "form" を別行に分けた直接構築
mod BadMultilineForm {
    pub def render(): Html =
        element(
            "form",
            Nil, Nil)
}
