///
/// ログイン用の CSRF 防御（IT9・TS16）。
///
/// # なぜセッションのトークンを使えないか
///
/// `SharedHttpAuth.usesSessionToken` が使うトークンは**セッションに紐づく**。
/// ログインはセッションが成立する前の操作であり、照合すべきトークンがまだ無い。
///
/// IT3 の判断（`usesSessionToken` のコメント）は次のとおりだった。
///
/// > ログイン自体を狙う CSRF は防げないが、本システムでは未認証で変更できる
/// > 業務データがないため実害がない。対策が必要になった時点で ADR で判断する。
///
/// **IT8 でこの前提が崩れた。** 見積（US01）が入り、営業担当者が業務データを
/// 作る画面が増えた。ログイン CSRF の害は「未認証でデータを変えられること」ではなく、
/// **被害者が攻撃者のアカウントでログインさせられ、その後の作業がすべて
/// 攻撃者の口座に記録されること**である。見積を作った営業はそれが自分のものだと
/// 思っており、攻撃者は後から読める。
///
/// # 方式: 二重送信 Cookie
///
/// `GET /login` で乱数を Cookie とフォームの両方に置き、`POST /login` で一致を見る。
/// **攻撃者は被害者のブラウザの Cookie を読めない**（同一オリジンポリシー）ため、
/// フォーム側の値を一致させられない。
///
/// セッションのトークンと**同じ 64 文字**にしてある。フォームの `_csrf` 欄は
/// 共有の `form` コンポーネントが描くため、長さが揃っていないと
/// テストの取り出し方が経路によって変わる。
///
mod SharedHttpLoginCsrf {
    use SharedHttp.Request

    /// Cookie 名。セッション Cookie とは別に持つ
    pub def cookieName(): String = "LOGIN_CSRF"

    ///
    /// トークンを組み立てる。
    ///
    /// **UUID 2 本からハイフンを除いて 64 文字にする**。セッションのトークンと
    /// 同じ長さにするためである——フォームの `_csrf` 欄は共有の `form`
    /// コンポーネントが描くため、長さが揃っていないと取り出し方が経路で変わる。
    ///
    pub def issueToken(first: String, second: String): String =
        let strip = v -> String.replace(src = "-", dst = "", v);
        let joined = strip(first) + strip(second);
        // UUID 2 本は 64 文字になるが、実装が変わっても長さを保つ
        if (String.length(joined) >= 64)
            String.slice(start = 0, end = 64, joined)
        else
            joined

    ///
    /// トークンを載せる Cookie ヘッダ値。
    ///
    /// **`SameSite=Strict` にする**。ログインの POST は同一サイトからしか
    /// 起こらないため、Lax より強くできる。これ自体が二重送信と独立した
    /// 防御層になる（Cookie が送られなければ照合は失敗する）。
    ///
    /// **`HttpOnly` にする**。フォームの値は HTML に出るが、
    /// Cookie 側は JavaScript から読めないようにする——
    /// XSS があったときに両方を揃えられると二重送信の意味が消える。
    ///
    pub def setCookie(token: String, secure: Bool): String =
        let base = "${cookieName()}=${token}; Path=/; HttpOnly; SameSite=Strict";
        if (secure) base + "; Secure" else base

    /// リクエストの Cookie からトークンを取り出す
    pub def fromRequest(req: Request): Option[String] =
        let SharedHttp.Request.Request(r) = req;
        SharedHttpAuth.cookieValue(Map.get("cookie", r#headers), cookieName())

    ///
    /// Cookie とフォームの値が一致するか。
    ///
    /// **どちらかが欠けていたら不一致**とする。Cookie だけ・フォームだけを
    /// 揃えても通らない。
    ///
    /// 比較は `SharedHttpAuth.isValidCsrf` に委ねる。**同じ規律を 2 つ書かない**——
    /// 定数時間比較と空文字の扱いは、片方だけ直すと片方が弱いまま残る
    /// （`escapeLike` が 3 箇所に増えた IT6 レビュー M6 と同じ形）。
    ///
    pub def matches(cookieToken: Option[String], formToken: Option[String]): Bool =
        SharedHttpAuth.isValidCsrf(cookieToken, formToken)
}
