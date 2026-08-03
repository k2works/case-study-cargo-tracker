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
    /// CSRF トークンの検証対象となるルートか。
    ///
    /// 未認証（`Anonymous`）のルートは対象外とする。ログインはセッションが成立する
    /// 前の操作であり、検証すべきトークンがまだ存在しない。
    ///
    /// ログイン自体を狙う CSRF（攻撃者の資格情報でログインさせる）は防げないが、
    /// 本システムでは未認証で変更できる業務データがないため実害がない。
    /// 対策が必要になった時点で、未認証セッションの発行を ADR で判断する。
    ///
    /// **判定そのものは合成ルートではなくここに置く**。`Anonymous` な POST ルートが
    /// 追加された瞬間に無防備になる境界であり、テストで固定できる場所に要る（IT3 レビュー M14）。
    ///
    pub def requiresCsrf(required: RequiredRole): Bool = match required {
        case RequiredRole.Anonymous => false
        case _                      => true
    }

    ///
    /// CSRF トークンを検証する。
    ///
    /// セッションのトークンと送信されたトークンが一致する場合のみ通す。
    /// **セッションにトークンがない（未認証）場合も拒否する**。未認証で状態を変更する
    /// 経路は現時点で存在せず、あれば設計の誤りだからである。
    ///
    /// 比較は定数時間で行う（IT3 レビュー L5）。`==` は最初の不一致で打ち切るため、
    /// 応答時間の差から先頭何文字が合っているかを推測できる。トークンは 64 文字あり
    /// 総当たりは現実的でないが、**秘密の比較に早期脱出を使わない**規律を守る。
    ///
    pub def isValidCsrf(sessionToken: Option[String], submitted: Option[String]): Bool =
        match (sessionToken, submitted) {
            case (Some(expected), Some(actual)) => expected != "" and constantTimeEquals(expected, actual)
            case _                              => false
        }

    ///
    /// 2 つの文字列を定数時間で比較する。
    ///
    /// 長さが違う場合は即座に偽を返す（長さは秘密ではない）。長さが同じ場合は
    /// **全文字を走査し**、途中で打ち切らずに差分を蓄積する。
    ///
    pub def constantTimeEquals(expected: String, actual: String): Bool =
        if (String.length(expected) != String.length(actual))
            false
        else
            let diff = List.zip(String.toList(expected), String.toList(actual))
                |> List.foldLeft((acc, pair) -> {
                    let (a, b) = pair;
                    Int32.bitwiseOr(acc, Int32.bitwiseXor(Char.toBmpCodePoint(a), Char.toBmpCodePoint(b)))
                }, 0);
            diff == 0

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
