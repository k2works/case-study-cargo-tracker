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
}
