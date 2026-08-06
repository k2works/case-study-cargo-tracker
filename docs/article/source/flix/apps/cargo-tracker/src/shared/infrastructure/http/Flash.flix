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
        /// 輸送見積を作成した（発行された見積番号。IT8・US01）
        case EstimateCreated(String)
        /// 追跡番号を発行した（発行された追跡番号。IT10・US14）
        case TrackingNumberIssued(String)
        ///
        /// 追跡番号を発行できなかった（**理由コード**。IT10・US14）。
        ///
        /// **文言を Cookie に入れない**。Cookie は利用者が書き換えられるため、
        /// 画面に出す文字列をそのまま載せると任意の文言を表示させられる。
        /// 載せるのは短い符号だけで、**文言はサーバが持つ**（`message`）。
        ///
        case TrackingNumberRejected(String)
        ///
        /// 荷役を登録した（**判定の符号**。IT10・US15）。
        ///
        /// 文言を Cookie に入れない（`TrackingNumberRejected` と同じ理由）。
        ///
        case HandlingRegistered(String)

        ///
        /// 荷役は記録したが、**他コンテキストへの反映に失敗した**（IT11・M6）。
        ///
        /// 記録そのものは残っている。失敗したのは追跡または予約への反映であり、
        /// 現場は次に何をすればよいかを知る必要がある。
        ///
        case HandlingNotReflected(String)
    }

    /// Cookie 名
    pub def cookieName(): String = "FLASH"

    // ---- 符号化 ----

    /// `種別:識別子` の形へ符号化する
    pub def encode(f: Flash): String = match f {
        case Flash.ShipperRegistered(code) => "shipperRegistered:${code}"
        case Flash.CargoBooked(bookingId)  => "cargoBooked:${bookingId}"
        case Flash.VoyageRegistered(number) => "voyageRegistered:${number}"
        case Flash.EstimateCreated(number)  => "estimateCreated:${number}"
        case Flash.TrackingNumberIssued(number) => "trackingIssued:${number}"
        case Flash.TrackingNumberRejected(code) => "trackingRejected:${code}"
        case Flash.HandlingRegistered(validity) => "handlingRegistered:${validity}"
        case Flash.HandlingNotReflected(code) => "handlingNotReflected:${code}"
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
        case "estimateCreated" =>
            if (isIssuedIdentifier("EST-", value)) Some(Flash.EstimateCreated(value)) else None
        case "trackingIssued" =>
            if (isTrackingNumberText(value)) Some(Flash.TrackingNumberIssued(value)) else None
        case "trackingRejected" =>
            if (isRejectionCode(value)) Some(Flash.TrackingNumberRejected(value)) else None
        case "handlingRegistered" =>
            if (isValidityCode(value)) Some(Flash.HandlingRegistered(value)) else None
        case "handlingNotReflected" =>
            if (isReflectionCode(value)) Some(Flash.HandlingNotReflected(value)) else None
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

    ///
    /// 追跡番号の形式（`TRK-YYYYMMDD-NNNN`）か。
    ///
    /// **形式の正典は Tracking の集約**（`TrackingModel.isWellFormed`）である。
    /// `shared` は Bounded Context を参照できない（`arch-lint` 規約 10）ため
    /// ここに同じ規則を持つが、**書き写したものが食い違わないことを
    /// `FlashTest.testTrackingNumberFormatAgreesWithTracking` が固定する**——
    /// 片方だけ緩めると「発行できるのに通知が出ない」が静かに起きる
    /// （IT6 レビュー H2 と同じ形）。
    ///
    def isTrackingNumberText(value: String): Bool =
        String.length(value) == 17 and
        String.startsWith(prefix = "TRK-", value) and
        String.charAt(12, value) == '-' and
        allDigits(String.slice(start = 4, end = 12, value)) and
        allDigits(String.slice(start = 13, end = 17, value))

    def allDigits(value: String): Bool =
        String.length(value) > 0 and String.forAll(c -> Char.isDigit(c), value)

    ///
    /// 発行できなかった理由の符号か。
    ///
    /// **既知の符号だけを通す**。任意の文字列を通すと、`message` の
    /// 既定分岐にそのまま流れる経路ができる。
    ///
    def isRejectionCode(value: String): Bool =
        value == "NOT_FOUND" or value == "NOT_CONFIRMED" or
        value == "ALREADY_ISSUED" or value == "NUMBERING_FAILED"

    /// 荷役の判定の符号か。**既知の符号だけを通す**
    def isValidityCode(value: String): Bool =
        value == "VALID" or value == "WARNED" or value == "MISROUTED"

    def isReflectionCode(value: String): Bool =
        value == "TRACKING" or value == "MISROUTING"

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
        case Flash.EstimateCreated(number) =>
            "見積を作成しました。見積番号は ${number} です。"
        case Flash.TrackingNumberIssued(number) =>
            "追跡番号を発行しました。追跡番号は ${number} です。"
        case Flash.TrackingNumberRejected(code) => rejectionMessage(code)
        case Flash.HandlingRegistered(validity) => handlingMessage(validity)
        case Flash.HandlingNotReflected(code) => notReflectedMessage(code)
    }

    ///
    /// 反映に失敗したときの文言（IT11・M6）。
    ///
    /// **記録は残っていることを先に言う**。「失敗しました」だけだと、
    /// 現場は同じ作業をもう一度登録し、履歴が二重になる。
    ///
    /// そのうえで**誰に連絡すればよいか**を言う。現場では直せない。
    ///
    def notReflectedMessage(code: String): String = match code {
        case "TRACKING" =>
            // 強調記法を文言に入れない（IT10 レビュー H7。画面にそのまま出る）
            "荷役を登録しました。ただし追跡情報に反映できませんでした。" +
            "荷主の追跡画面は更新されていません。登録し直さず、" +
            "追跡番号を控えて追跡管理者へご連絡ください。"
        case _ =>
            "荷役を登録しました。ただし経路逸脱を予約へ報告できませんでした。" +
            "登録し直さず、追跡番号を控えて経路設計者へ直接ご連絡ください。"
    }

    ///
    /// 荷役の登録結果の文言。**サーバが持つ**（Cookie には符号だけ）。
    ///
    /// 判定ごとに次の一手が違う——正常なら何もしなくてよく、
    /// 要確認なら場所を見直し、経路逸脱なら経路設計者へ連絡する。
    ///
    def handlingMessage(validity: String): String = match validity {
        case "WARNED" =>
            "荷役を登録しました。作業場所が予定と異なります。場所に誤りがないか確認してください。"
        case "MISROUTED" =>
            "荷役を登録しました。貨物が予定と違う経路に乗っています。" +
            "経路設計者へご連絡ください。"
        case _ => "荷役を登録しました。"
    }

    ///
    /// 発行できなかった理由の文言。**サーバが持つ**（Cookie には符号だけ）。
    ///
    /// いずれも「次に何をすればよいか」を言う（IT9 レビュー L・利用者代表）。
    ///
    def rejectionMessage(code: String): String = match code {
        case "NOT_FOUND" =>
            "この予約は見つかりませんでした。予約一覧から選び直してください。"
        case "NOT_CONFIRMED" =>
            "この予約は確定されていないため追跡番号を発行できません。営業担当者による確定をお待ちください。"
        case "ALREADY_ISSUED" =>
            "この予約には既に追跡番号が発行されています。画面に出ている番号を荷主へご案内ください。"
        case _ =>
            "追跡番号の採番に失敗しました。時間をおいて再度お試しください。"
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
