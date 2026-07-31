///
/// 認可の判定と Cookie の扱い。
///
/// 判定は**ルーティング表の `RequiredRole` のみ**に基づく純粋関数として切り出す。
/// パス文字列での分岐を作らないのは、認可の正典がルート定義であるという設計
/// （[バックエンドアーキテクチャ](../../../../../../docs/design/architecture_backend.md)）を
/// 実装でも保つためである。判定が純粋なので、全ルート × 全ロールの可否を表でテストできる。
///
mod SharedHttpAuth {
    use SharedHttp.{Request, Response, RequiredRole, headers, status, body}
    use SharedHttp.Response.Response
    use SharedSecurity.{Principal, Role}
    use SharedSecurity.role

    /// セッション Cookie の名前
    pub def sessionCookieName(): String = "SESSIONID"

    /// 認可の判定結果
    pub enum Decision with Eq {
        /// 通してよい
        case Allow
        /// 未認証。ログイン画面へ誘導する
        case RequireLogin
        /// 認証済みだがロールが不足している
        case Deny
    }

    ///
    /// 認可を判定する。
    ///
    /// `Admin` がすべてを通れるのは、ロール表で「全機能」と定義されているためである。
    /// ルート定義に `Admin` を書き漏らしても管理者が締め出されないよう、ここで保証する。
    ///
    pub def decide(required: RequiredRole, p: Option[Principal]): Decision =
        match (required, p) {
            case (RequiredRole.Anonymous, _)   => Decision.Allow
            case (_, None)                     => Decision.RequireLogin
            case (_, Some(user)) if role(user) == Role.Admin => Decision.Allow
            case (RequiredRole.RoleRequired(r), Some(user)) =>
                if (role(user) == r) Decision.Allow else Decision.Deny
            case (RequiredRole.RoleAnyOf(rs), Some(user)) =>
                if (List.memberOf(role(user), rs)) Decision.Allow else Decision.Deny
        }

    ///
    /// リクエストヘッダから Cookie の値を取り出す。
    ///
    /// `Cookie: a=1; SESSIONID=xyz; b=2` の形式を解析する。
    ///
    pub def cookieValue(cookieHeader: Option[String], name: String): Option[String] =
        match cookieHeader {
            case None => None
            case Some(raw) =>
                String.splitOn(substr = ";", raw)
                    |> List.map(String.trim)
                    |> List.filterMap(pair -> {
                        match String.indexOf(substr = "=", pair) {
                            case None => None
                            case Some(i) =>
                                let key = String.slice(start = 0, end = i, pair);
                                let value = String.slice(start = i + 1, end = String.length(pair), pair);
                                if (key == name) Some(value) else None
                        }
                    })
                    |> List.head
        }

    ///
    /// セッション Cookie を設定するヘッダ値。
    ///
    /// `HttpOnly` は JavaScript からの読み取りを防ぎ、XSS でセッションを盗まれる経路を塞ぐ。
    /// `SameSite=Lax` は他サイトからの POST に Cookie を送らないことで CSRF の一次防御になる。
    /// `Secure` は本番（HTTPS）でのみ付ける。開発は HTTP のため付けると Cookie が送られない。
    ///
    pub def setSessionCookie(sessionId: String, secure: Bool): String =
        let base = "${sessionCookieName()}=${sessionId}; Path=/; HttpOnly; SameSite=Lax";
        if (secure) base + "; Secure" else base

    /// セッション Cookie を削除するヘッダ値（ログアウト用）
    pub def clearSessionCookie(secure: Bool): String =
        let base = "${sessionCookieName()}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
        if (secure) base + "; Secure" else base

    /// レスポンスにヘッダを追加する
    pub def withHeader(res: Response, name: String, value: String): Response =
        let Response.Response(r) = res;
        Response.Response({ status = r#status, headers = Map.insert(name, value, r#headers), body = r#body })

    /// CSRF トークンのフォームフィールド名
    pub def csrfFieldName(): String = "_csrf"

    ///
    /// 状態を変更するメソッドか。
    ///
    /// CSRF トークンの検証対象を決める。GET / HEAD は状態を変更しない前提であり、
    /// 検証すると公開追跡のような未認証の照会まで通らなくなる。
    ///
    pub def isStateChanging(method: SharedHttp.HttpMethod): Bool = match method {
        case SharedHttp.HttpMethod.Get => false
        case _                         => true
    }

    ///
    /// CSRF トークンを検証する。
    ///
    /// セッションのトークンと送信されたトークンが一致する場合のみ通す。
    /// **セッションにトークンがない（未認証）場合も拒否する**。未認証で状態を変更する
    /// 経路は現時点で存在せず、あれば設計の誤りだからである。
    ///
    pub def isValidCsrf(sessionToken: Option[String], submitted: Option[String]): Bool =
        match (sessionToken, submitted) {
            case (Some(expected), Some(actual)) => expected != "" and expected == actual
            case _                              => false
        }

    /// 403 応答
    pub def forbidden(): Response =
        Response.Response({
            status = 403,
            headers = Map#{"Content-Type" => "text/plain; charset=utf-8"},
            body = "アクセス権限がありません"
        })

    /// リダイレクト応答
    pub def redirect(location: String): Response =
        Response.Response({
            status = 302,
            headers = Map#{"Location" => location},
            body = ""
        })
}
