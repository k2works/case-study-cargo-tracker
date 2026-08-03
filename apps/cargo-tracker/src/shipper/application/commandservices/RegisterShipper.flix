///
/// 荷主登録のユースケース（US02・US03）。
///
/// **判断の順序に意味がある**。入力の検証 → 重複判定 → 保存の順に進む。
/// 順序が逆だと、形式の壊れた入力で重複検索が走り、利用者は誤りを 2 回に分けて
/// 知らされることになる。この順序は `RegisterShipperTest` が固定している。
///
/// 要求する効果はシグネチャにすべて現れる。隠れた依存は作れない。
///
mod ShipperRegister {
    use SharedIdGen.IdGen
    use ShipperModel.{Shipper, ShipperError}
    use ShipperModel.{registerIndividual, registerCorporate, emailOf}
    use ShipperRepo.ShipperRepo

    ///
    /// 登録の指示。
    ///
    /// 画面から届く**文字列のまま**受け取る。値オブジェクトへ変換できない入力を
    /// そのまま画面へ戻す必要があるため（[UI 設計](../../../../../../docs/design/ui_design.md)
    /// の `FormView`）、ここでドメイン型を要求しない。
    ///
    pub enum RegisterCommand({
        name = String,
        email = String,
        phone = Option[String],
        address = Option[String],
        corporate = Option[(String, Int32)]
    })

    ///
    /// 登録の結果。
    ///
    /// 重複時に**既存の荷主を添える**のは、画面が「この荷主を使う」という導線を
    /// 出せるようにするためである（US02 受入基準 2・Try T4）。
    /// 「重複しています」だけでは利用者は次に何もできない。
    ///
    pub enum RegisterResult {
        case Registered(Shipper)
        case DuplicateEmail(Shipper)
        case Invalid(ShipperError)
    }

    ///
    /// 結果の種別名。
    ///
    /// `Shipper` はレコードであり `ToString` を導出できない。テストの失敗メッセージで
    /// 「何が返ったか」を示せないと、落ちたときに原因を探す手掛かりが消える。
    ///
    pub def resultLabel(r: RegisterResult): String = match r {
        case RegisterResult.Registered(_)     => "Registered"
        case RegisterResult.DuplicateEmail(_) => "DuplicateEmail"
        case RegisterResult.Invalid(e)        => "Invalid(${e})"
    }

    /// 個人荷主の登録指示を組み立てる
    pub def individualCommand(name: String, email: String,
                              phone: Option[String], address: Option[String]): RegisterCommand =
        RegisterCommand.RegisterCommand({
            name = name, email = email, phone = phone, address = address, corporate = None
        })

    /// 法人荷主の登録指示を組み立てる
    pub def corporateCommand(name: String, email: String,
                             phone: Option[String], address: Option[String],
                             contractNumber: String, discountRate: Int32): RegisterCommand =
        RegisterCommand.RegisterCommand({
            name = name, email = email, phone = phone, address = address,
            corporate = Some((contractNumber, discountRate))
        })

    ///
    /// 荷主を登録する。
    ///
    /// **順序**:
    ///
    /// 1. 入力を検証して集約を作る（形式の誤りをここで確定させる）
    /// 2. メールの重複を判定する（検証を通った入力に対してのみ検索する）
    /// 3. 保存する
    ///
    /// 1 と 2 を入れ替えないこと。壊れた入力で DB を引く意味がないうえ、
    /// 利用者へ誤りを小出しにすることになる。
    ///
    pub def register(cmd: RegisterCommand): RegisterResult \ ShipperRepo + IdGen =
        let RegisterCommand.RegisterCommand(c) = cmd;
        let shipperId = IdGen.newId();
        let created = match c#corporate {
            case None => registerIndividual(shipperId, c#name, c#email, c#phone, c#address)
            case Some((contractNumber, discountRate)) =>
                registerCorporate(shipperId, c#name, c#email, c#phone, c#address,
                                  contractNumber, discountRate)
        };
        match created {
            case Err(e) => RegisterResult.Invalid(e)
            case Ok(shipper) =>
                match findDuplicate(emailOf(shipper)) {
                    case Some(found) => RegisterResult.DuplicateEmail(found)
                    case None        =>
                        ShipperRepo.save(shipper);
                        RegisterResult.Registered(shipper)
                }
        }

    ///
    /// 同じメールアドレスの荷主を探す。
    ///
    /// **大文字小文字を区別しない**。`Yamada@Example.com` と `yamada@example.com` は
    /// 同じ宛先であり、区別すると同じ人が別の荷主として二重登録される。
    /// 正規化はここで行い、保存する値は利用者が入力したままにする
    /// （表示は入力どおりであってほしい）。
    ///
    def findDuplicate(email: String): Option[Shipper] \ ShipperRepo =
        ShipperRepo.findByEmail(String.toLowerCase(email))

    /// 荷主を一覧する
    pub def listAll(): List[Shipper] \ ShipperRepo =
        ShipperRepo.findAll()

    /// 荷主 ID で 1 件引く
    pub def findByShipperId(shipperId: String): Option[Shipper] \ ShipperRepo =
        ShipperRepo.findByShipperId(shipperId)
}
