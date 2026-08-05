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
    ///
    /// # 名前について（IT10・TS17。IT9 レビュー M6）
    ///
    /// IT9 までの名前は `requiresCsrf`（CSRF 検証が要るか）だった。TS16 で
    /// **未認証の経路も一律で検証する**形に変えたため、`false` を返しても
    /// 検証はされる——`false` を見た次の実装者は「検証されない」と読む。
    /// 意味に合わせて `usesSessionToken`（セッションのトークンと照合するか）へ改名した。
    ///
    /// **本関数が答えるのは「どのトークンと照合するか」だけである**（IT9・TS16）。
    /// 未認証（`Anonymous`）のルートも**検証はする**。照合先が
    /// セッションのトークンではなく、ログイン画面が発行した Cookie になる
    /// （`SharedHttpLoginCsrf`。合成ルートの `expectedCsrf` が選ぶ）。
    ///
    /// # IT3 の判断とその前提の崩れ
    ///
    /// IT3 は「未認証のルートは検証しない」と決め、理由をこう書いていた。
    ///
    /// > ログイン自体を狙う CSRF は防げないが、本システムでは未認証で変更できる
    /// > 業務データがないため実害がない。
    ///
    /// **IT8 でこの前提が崩れた**。見積（US01）が入り、営業担当者が業務データを
    /// 作る画面が増えた。ログイン CSRF の害は「未認証でデータを変えられること」
    /// ではなく、**被害者が攻撃者のアカウントでログインさせられ、その後の作業が
    /// すべて攻撃者の口座に記録されること**である。
    ///
    /// 前提に依存した判断は、前提が変わったときに**誰も見直さない**まま残る。
    /// IT9 で二重送信 Cookie を入れ、`Anonymous` でも検証する形にした。
    ///
    /// **判定そのものは合成ルートではなくここに置く**。`Anonymous` な POST ルートが
    /// 追加された瞬間に無防備になる境界であり、テストで固定できる場所に要る（IT3 レビュー M14）。
    ///
    pub def usesSessionToken(required: RequiredRole): Bool = match required {
        case RequiredRole.Anonymous => false
        case _                      => true
    }

    ///
    /// CSRF トークンを検証する。
    ///
    /// 渡されたトークンと送信されたトークンが一致する場合のみ通す。
    /// **トークンが無い場合も拒否する**。
    ///
    /// 照合先はセッションのトークンとは限らない（IT9・TS16）。未認証の経路では
    /// ログイン画面が発行した Cookie のトークンが渡される
    /// （`SharedHttpLoginCsrf`。合成ルートの `expectedCsrf` が選ぶ）。
    /// IT9 まで本コメントは「未認証で状態を変更する経路は存在しない」と
    /// **崩れた前提のまま**残っていた（IT9 レビュー M6）。
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
