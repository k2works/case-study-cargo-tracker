///
/// フラッシュメッセージのテスト。
///
/// **なぜ Cookie に置くか**: 登録完了は PRG のため、値を作るリクエストと表示する
/// リクエストが別になる。セッション（DB）へ置くと、**表示時に消す操作が書き込みになり**、
/// `TxMode.ReadOnly` の一覧画面で消せない（IT4 で `readOnly` は末尾 `rollback` に直したため、
/// 消したつもりが戻る）。表示のたびに `TxMode.Write` にするのは、読むだけの画面に
/// 書き込みトランザクションを取らせることになり筋が悪い。
///
/// **Cookie は利用者が書き換えられる**。よって中身を信用しない設計にする。
/// 保持するのは「種別」と「発行された識別子」だけで、**表示文言はサーバが持つ**。
/// 識別子は形式を検証する。偽装しても、自分の画面に正しい形の見慣れたメッセージが
/// 出るだけで、他人には影響しない。
///
mod FlashTest {
    use SharedHttpFlash.{Flash, encode, decode, message, setCookie, clearCookie, cookieName}

    // ---- 往復 ----

    @Test
    def testShipperRegisteredRoundTrip(): Unit \ Assert =
        let f = Flash.ShipperRegistered("SHP-1A2B3C4D");
        Assert.assertEq(expected = Some(f), decode(encode(f)))

    @Test
    def testCargoBookedRoundTrip(): Unit \ Assert =
        let f = Flash.CargoBooked("BK-1A2B3C4D");
        Assert.assertEq(expected = Some(f), decode(encode(f)))

    // ---- 偽装された Cookie を受け付けない（軸 1・軸 3） ----

    @Test
    def testRejectsUnknownKind(): Unit \ Assert =
        Assert.assertEq(expected = None, decode("accountCredited:1000000"))

    @Test
    def testRejectsMalformedShipperCode(): Unit \ Assert =
        // 種別は正しいが識別子の形式が違う
        Assert.assertEq(expected = None, decode("shipperRegistered:<script>"))

    @Test
    def testRejectsShipperCodeWithWrongPrefix(): Unit \ Assert =
        Assert.assertEq(expected = None, decode("shipperRegistered:BK-1A2B3C4D"))

    @Test
    def testRejectsBookingIdWithWrongPrefix(): Unit \ Assert =
        Assert.assertEq(expected = None, decode("cargoBooked:SHP-1A2B3C4D"))

    @Test
    def testRejectsShipperCodeOfWrongLength(): Unit \ Assert =
        Assert.assertEq(expected = None, decode("shipperRegistered:SHP-1A2B"))

    @Test
    def testRejectsLowercaseIdentifier(): Unit \ Assert =
        // 採番は常に大文字（`generateCode` / `generateBookingId`）
        Assert.assertEq(expected = None, decode("shipperRegistered:SHP-1a2b3c4d"))

    @Test
    def testRejectsEmpty(): Unit \ Assert =
        Assert.assertEq(expected = None, decode(""))

    @Test
    def testRejectsMissingSeparator(): Unit \ Assert =
        Assert.assertEq(expected = None, decode("shipperRegistered"))

    @Test
    def testRejectsExtraSeparator(): Unit \ Assert =
        Assert.assertEq(expected = None, decode("shipperRegistered:SHP-1A2B3C4D:extra"))

    @Test
    def testRejectsFullWidthIdentifier(): Unit \ Assert =
        // 全角英数字を通さない（IT4 で `Char.isDigit` が全角を通した。軸 1-5）
        Assert.assertEq(expected = None, decode("shipperRegistered:SHP-１２３４ＡＢＣＤ"))

    // ---- 表示文言はサーバが持つ（Cookie の中身は文言にしない） ----

    @Test
    def testShipperMessageContainsIssuedCode(): Unit \ Assert =
        let m = message(Flash.ShipperRegistered("SHP-1A2B3C4D"));
        Assert.assertEq(expected = true, String.contains(substr = "SHP-1A2B3C4D", m))

    @Test
    def testCargoMessageContainsIssuedBookingId(): Unit \ Assert =
        let m = message(Flash.CargoBooked("BK-1A2B3C4D"));
        Assert.assertEq(expected = true, String.contains(substr = "BK-1A2B3C4D", m))

    @Test
    def testMessagesDifferByKind(): Unit \ Assert =
        let a = message(Flash.ShipperRegistered("SHP-1A2B3C4D"));
        let b = message(Flash.CargoBooked("BK-1A2B3C4D"));
        Assert.assertEq(expected = false, a == b)

    // ---- Cookie ヘッダ ----

    @Test
    def testSetCookieCarriesEncodedValue(): Unit \ Assert =
        let h = setCookie(Flash.ShipperRegistered("SHP-1A2B3C4D"), false);
        Assert.assertEq(expected = true,
                        String.contains(substr = "${cookieName()}=shipperRegistered:SHP-1A2B3C4D", h))

    ///
    /// **`HttpOnly` を付ける**。フラッシュは JavaScript から読む必要がない。
    /// 読めるようにする理由がないものは読めなくしておく。
    ///
    @Test
    def testSetCookieIsHttpOnly(): Unit \ Assert =
        let h = setCookie(Flash.ShipperRegistered("SHP-1A2B3C4D"), false);
        Assert.assertEq(expected = true, String.contains(substr = "HttpOnly", h))

    @Test
    def testSetCookieIsSameSiteLax(): Unit \ Assert =
        let h = setCookie(Flash.ShipperRegistered("SHP-1A2B3C4D"), false);
        Assert.assertEq(expected = true, String.contains(substr = "SameSite=Lax", h))

    @Test
    def testSetCookieAddsSecureWhenRequested(): Unit \ Assert =
        let h = setCookie(Flash.ShipperRegistered("SHP-1A2B3C4D"), true);
        Assert.assertEq(expected = true, String.contains(substr = "Secure", h))

    @Test
    def testSetCookieOmitsSecureForPlainHttp(): Unit \ Assert =
        let h = setCookie(Flash.ShipperRegistered("SHP-1A2B3C4D"), false);
        Assert.assertEq(expected = false, String.contains(substr = "Secure", h))

    ///
    /// **1 回だけ表示される**ことの根拠。表示時に `Max-Age=0` で消す。
    /// これが無いと再読み込みのたびに同じ成功メッセージが出る。
    ///
    @Test
    def testClearCookieExpiresImmediately(): Unit \ Assert =
        let h = clearCookie(false);
        Assert.assertEq(expected = true, String.contains(substr = "Max-Age=0", h))

    @Test
    def testClearCookieTargetsTheSameCookie(): Unit \ Assert =
        let h = clearCookie(false);
        Assert.assertEq(expected = true, String.contains(substr = "${cookieName()}=", h))

    // ---- 追跡番号（IT10・US14） ----

    def issuedNumber(): String = "TRK-20260805-0001"

    @Test
    def testTrackingNumberIssuedRoundTrip(): Unit \ Assert =
        let f = Flash.TrackingNumberIssued(issuedNumber());
        Assert.assertEq(expected = Some(f), decode(encode(f)))

    @Test
    def testTrackingNumberRejectedRoundTrip(): Unit \ Assert =
        let f = Flash.TrackingNumberRejected("NOT_CONFIRMED");
        Assert.assertEq(expected = Some(f), decode(encode(f)))

    ///
    /// **追跡番号の形式判定が Tracking の集約と一致すること**。
    ///
    /// `shared` は Bounded Context を参照できない（`arch-lint` 規約 10）ため、
    /// `Flash` は形式の規則を**書き写して**持っている。書き写したものは
    /// 片方だけ変わる——`escapeLike` が 3 箇所に増えた IT6 M6 と同じ形である。
    ///
    /// 参照できないなら**一致することをテストで固定する**。
    /// この式が偽になる入力: どちらか一方だけ桁数や接頭辞を変えた実装
    /// （発行できるのに通知が出ない、あるいはその逆）。
    ///
    @Test
    def testTrackingNumberFormatAgreesWithTracking(): Unit \ Assert =
        let samples =
            "TRK-20260805-0001" ::   // 正常
            "TRK-20991231-9999" ::   // 正常（境界）
            "" ::
            "TRK-20260805-001" ::    // 連番 3 桁
            "TRK-20260805-00011" ::  // 連番 5 桁
            "TRX-20260805-0001" ::   // 接頭辞違い
            "TRK-2026080-50001" ::   // 区切りの位置違い
            "TRK-2026080X-0001" ::   // 日付に数字以外
            "TRK-20260805-00A1" :: Nil;
        List.forEach(v -> {
            // `Flash` 側の判定は `decode` を通してしか観測できない
            let flashAccepts = not Option.isEmpty(decode("trackingIssued:" + v));
            let trackingAccepts = TrackingModel.isWellFormed(v);
            if (flashAccepts == trackingAccepts) ()
            else Assert.fail(
                "追跡番号の形式判定が食い違いました: ${v} / " +
                "Flash=${flashAccepts} Tracking=${trackingAccepts}")
        }, samples)

    ///
    /// **形式に合わない追跡番号の通知を復号しないこと**。
    ///
    /// 任意の文字列を通すと、画面に「追跡番号を発行しました。追跡番号は
    /// 〈任意の文字列〉です」と出させられる。
    ///
    @Test
    def testRejectsMalformedTrackingNumberInCookie(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(decode("trackingIssued:どうぞご送金ください")));
        Assert.assertTrue(Option.isEmpty(decode("trackingIssued:")))

    ///
    /// **未知の理由コードを復号しないこと**。
    ///
    /// 理由は符号だけを Cookie に載せ、**文言はサーバが持つ**。
    /// 未知の符号を通すと、`message` の既定分岐へ任意の値が流れる経路ができる。
    ///
    @Test
    def testRejectsUnknownRejectionCode(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(decode("trackingRejected:任意の文言")));
        Assert.assertTrue(Option.isEmpty(decode("trackingRejected:SOMETHING")))

    ///
    /// 既知の理由コードはすべて「次に何をすればよいか」を言うこと。
    ///
    /// 状況の説明だけで終わると、利用者は自分の操作が悪いのか
    /// 誰かが先に触ったのかを判断できない（IT9 レビュー・利用者代表）。
    ///
    @Test
    def testEveryRejectionCodeHasGuidance(): Unit \ Assert =
        let codes = "NOT_FOUND" :: "NOT_CONFIRMED" :: "ALREADY_ISSUED" ::
                    "NUMBERING_FAILED" :: Nil;
        List.forEach(code -> match decode("trackingRejected:" + code) {
            case None    => Assert.fail("既知の理由コードを復号できませんでした: ${code}")
            case Some(f) => {
                let text = message(f);
                Assert.assertTrue(String.length(text) > 0);
                Assert.assertTrue(String.contains(substr = "ください", text))
            }
        }, codes)
}
