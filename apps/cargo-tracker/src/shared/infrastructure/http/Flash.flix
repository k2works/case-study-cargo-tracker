///
/// フラッシュメッセージ（PRG の遷移先で 1 回だけ表示する通知）。
///
/// **解決する問題**: IT4 では荷主コードも予約番号も、発行されたあと画面のどこにも
/// 出なかった。利用者は一覧から自分の行を目で探すことになり、
/// 別途 Excel の台帳を作る運用になる。**一元管理という目的そのものが崩れる**。
///
/// **保存先に Cookie を選んだ理由**: 登録は PRG のため、値を作るリクエストと
/// 表示するリクエストが別になる。セッション（DB）へ置くと**表示時に消す操作が
/// 書き込みになり**、`TxMode.ReadOnly` の一覧画面では消せない
/// （IT4 で `readOnly` を末尾 `rollback` に直したため、消したつもりが戻る）。
/// 表示のたびに `TxMode.Write` へ上げるのは、読むだけの画面に書き込み
/// トランザクションを取らせることになり筋が悪い。
///
/// **Cookie は利用者が書き換えられる**。そこで中身を信用しない形にする。
///
/// - 保持するのは**種別と発行された識別子だけ**。表示文言はサーバが持つ
/// - 識別子は**形式を検証**する（接頭辞・長さ・大文字英数字）
/// - 未知の種別・不正な形式は**メッセージなし**として扱う
///
/// 偽装しても、自分の画面に正しい形の見慣れたメッセージが出るだけである。
///
mod SharedHttpFlash {
    use SharedHttp.Request
    use SharedHttpAuth.cookieValue

    ///
    /// 表示する通知。
    ///
    /// **文言ではなく出来事を持つ**。文言を Cookie に載せると、利用者が
    /// 任意の文字列を自分の画面へ出せるようになる（エスケープするので実害は
    /// 小さいが、載せる理由もない）。
    ///
    pub enum Flash with Eq, ToString {
        /// 荷主を登録した（発行された荷主コード）
        case ShipperRegistered(String)
        /// 貨物を予約した（発行された予約番号）
        case CargoBooked(String)
        /// 航海スケジュールを登録・更新した（航海番号）
        case VoyageRegistered(String)
    }

    /// Cookie 名
    pub def cookieName(): String = "FLASH"

    // ---- 符号化 ----

    /// `種別:識別子` の形へ符号化する
    pub def encode(f: Flash): String = match f {
        case Flash.ShipperRegistered(code) => "shipperRegistered:${code}"
        case Flash.CargoBooked(bookingId)  => "cargoBooked:${bookingId}"
        case Flash.VoyageRegistered(number) => "voyageRegistered:${number}"
    }

    ///
    /// Cookie の値を復号する。**信用しない入力として扱う**。
    ///
    /// 区切りが 1 つでない・種別が未知・識別子の形式が違う場合は `None` を返す。
    ///
    pub def decode(raw: String): Option[Flash] =
        match String.splitOn(substr = ":", raw) {
            case kind :: value :: Nil => decodeKind(kind, value)
            case _                    => None
        }

    def decodeKind(kind: String, value: String): Option[Flash] = match kind {
        case "shipperRegistered" =>
            if (isIssuedIdentifier("SHP-", value)) Some(Flash.ShipperRegistered(value)) else None
        case "cargoBooked" =>
            if (isIssuedIdentifier("BK-", value)) Some(Flash.CargoBooked(value)) else None
        case "voyageRegistered" =>
            // 航海番号は**利用者が決める**（採番しない）。`SHP-`/`BK-` のような
            // 固定の形を持たないため、長さと文字種だけを検査する。
            //
            // **規則は共有カーネルが持つ**（`SharedCalendarText.isUpperAlnumCode`）。
            // ここに同じ規則を書き写すと、片方だけ緩めたときに
            // 「登録できるのに通知が出ない」が静かに起きる（IT6 レビュー H2）。
            // 集約（`RoutingModel.isVoyageNumberText`）も同じ関数を呼ぶ
            if (SharedCalendarText.isUpperAlnumCode(20, value))
                Some(Flash.VoyageRegistered(value))
            else None
        case _ => None
    }

    ///
    /// 採番された識別子の形式か。
    ///
    /// `generateCode` / `generateBookingId` はいずれも
    /// **接頭辞 + 大文字英数字 8 文字**を作る。それ以外は受け付けない。
    ///
    /// `Char.isLetter` / `Char.isDigit` は使わない。**全角英数字や他言語の文字を
    /// 通してしまう**（IT4 で `日本大阪港` が UN/LOCODE として通った原因）。
    ///
    def isIssuedIdentifier(prefix: String, value: String): Bool =
        String.startsWith(prefix = prefix, value) and
        String.length(value) == String.length(prefix) + 8 and
        String.slice(start = String.length(prefix), end = String.length(value), value)
            |> String.toList
            |> List.forAll(isUpperAlnum)

    def isUpperAlnum(c: Char): Bool =
        (c >= 'A' and c <= 'Z') or (c >= '0' and c <= '9')

    // ---- 表示 ----

    /// 画面に出す文言。**サーバが持つ**（Cookie には入れない）
    pub def message(f: Flash): String = match f {
        case Flash.ShipperRegistered(code) =>
            "荷主を登録しました。荷主コードは ${code} です。"
        case Flash.CargoBooked(bookingId) =>
            "貨物を予約しました。予約番号は ${bookingId} です。"
        case Flash.VoyageRegistered(number) =>
            "航海スケジュールを保存しました。航海番号は ${number} です。"
    }

    // ---- Cookie ----

    ///
    /// 通知を載せる Cookie ヘッダ値。
    ///
    /// `HttpOnly` を付けるのは、JavaScript から読む理由がないためである。
    /// 読めるようにする理由がないものは読めなくしておく。
    ///
    pub def setCookie(f: Flash, secure: Bool): String =
        let base = "${cookieName()}=${encode(f)}; Path=/; HttpOnly; SameSite=Lax";
        if (secure) base + "; Secure" else base

    ///
    /// 通知を消す Cookie ヘッダ値。
    ///
    /// **表示したら必ず消す**。消さないと再読み込みのたびに同じ成功メッセージが出て、
    /// 「登録できていないのでは」と利用者を迷わせる。
    ///
    pub def clearCookie(secure: Bool): String =
        let base = "${cookieName()}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0";
        if (secure) base + "; Secure" else base

    /// リクエストの Cookie から通知を取り出す
    pub def fromRequest(req: Request): Option[Flash] =
        let SharedHttp.Request.Request(r) = req;
        cookieValue(Map.get("cookie", r#headers), cookieName())
            |> Option.flatMap(decode)
}
