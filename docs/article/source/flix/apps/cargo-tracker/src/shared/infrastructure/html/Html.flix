///
/// HTML 生成の DSL。
///
/// テンプレートエンジンを使わず、型付き ADT と結合子で HTML を構築する（ADR-0002）。
/// テキストノードは描画時に必ずエスケープされる。エスケープ回避は `rawUnsafe` のみで可能とし、
/// 使用箇所は arch-lint の許可リストで管理する。
///
/// 画面関数は本モジュールの `Html` を返す純粋関数として書く。効果を要求しないため
/// テストで `render` した文字列を直接検証できる。
///
mod SharedHtml {

    /// HTML ノード
    pub enum Html {
        case Element(String, List[Attr], List[Html])
        case Text(String)
        case RawUnsafe(String)
        case Fragment(List[Html])
        case Empty
    }

    /// HTML 属性
    pub enum Attr {
        case Attr(String, String)
        case BoolAttr(String)
    }

    /// 内容を持たない空要素（void element）
    def isVoidElement(tag: String): Bool =
        List.memberOf(tag, "area" :: "base" :: "br" :: "col" :: "embed" :: "hr" ::
                           "img" :: "input" :: "link" :: "meta" :: "source" ::
                           "track" :: "wbr" :: Nil)

    /// テキストノードのエスケープ
    pub def escapeText(s: String): String =
        s |> String.replace(src = "&", dst = "&amp;")
          |> String.replace(src = "<", dst = "&lt;")
          |> String.replace(src = ">", dst = "&gt;")

    /// 属性値のエスケープ（引用符も対象にする）
    pub def escapeAttr(s: String): String =
        escapeText(s)
          |> String.replace(src = "\"", dst = "&quot;")
          |> String.replace(src = "'", dst = "&#39;")

    /// 属性を描画する
    def renderAttr(attr: Attr): String = match attr {
        case Attr.Attr(name, value) => " ${name}=\"${escapeAttr(value)}\""
        case Attr.BoolAttr(name)    => " ${name}"
    }

    /// HTML を文字列へ描画する（純粋関数）
    pub def render(html: Html): String = match html {
        case Html.Empty              => ""
        case Html.Text(s)            => escapeText(s)
        case Html.RawUnsafe(s)       => s
        case Html.Fragment(children) => children |> List.map(render) |> List.join("")
        case Html.Element(tag, attrs, children) =>
            let attrsStr = attrs |> List.map(renderAttr) |> List.join("");
            if (isVoidElement(tag))
                "<${tag}${attrsStr}>"
            else
                let inner = children |> List.map(render) |> List.join("");
                "<${tag}${attrsStr}>${inner}</${tag}>"
    }

    /// ドキュメント全体を描画する（DOCTYPE 付き）
    pub def renderDocument(html: Html): String =
        "<!DOCTYPE html>" + render(html)

    // ---- 要素の構築子 ----

    pub def element(tag: String, attrs: List[Attr], children: List[Html]): Html =
        Html.Element(tag, attrs, children)

    pub def text(s: String): Html = Html.Text(s)

    pub def fragment(children: List[Html]): Html = Html.Fragment(children)

    pub def empty(): Html = Html.Empty

    // ---- 属性の構築子 ----

    pub def attr(name: String, value: String): Attr = Attr.Attr(name, value)

    pub def class_(value: String): Attr = Attr.Attr("class", value)

    pub def id(value: String): Attr = Attr.Attr("id", value)
}
