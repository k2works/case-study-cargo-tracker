/// 負例: method を書かない直接構築
///
/// HTML の `form` は `method` を省くと GET になるが、**書き手がそれを意図したか
/// 読み取れない**。POST のつもりで書き忘れた可能性を、規約は区別できない。
/// 免除するのは `method="get"` を明示したものだけとする。
mod BadImplicitMethodForm {
    def assignForm(bookingId: String, csrfToken: Option[String]): Html =
        element("form", attr("action", "/bookings/${bookingId}/assign-to-routing") :: Nil,
            element("button", attr("type", "submit") ::
                              class_("btn btn-primary") :: Nil,
                text("経路設計へ引き渡す") :: Nil) :: Nil)
}
