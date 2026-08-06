///
/// Shipper Context のドメインモデル。
///
/// [ドメインモデル設計](../../../../../../docs/design/domain-model.md) の
/// 「2. Shipper Context」を正典とする。
///
/// **効果を一切要求しない**。永続化・重複判定・ID 生成はアプリケーション層の責務であり、
/// ここにあるのは「荷主として成立する形か」を判定するルールだけである。
///
/// **`CorporateShipper` の表現**: ドメインモデル設計は `CorporateShipper extends Shipper` の
/// 継承で描いているが、Flix に継承はない。`Shipper` が `ShipperType` と法人固有フィールド
/// （オプション）を持ち、「CORPORATE なら契約番号・割引率が必須」を不変条件として
/// 生成時に強制する形へ写す。
///
mod ShipperModel {

    ///
    /// 荷主種別。
    ///
    /// 法人は割引の対象になる（[ドメインモデル設計](../../../../../../docs/design/domain-model.md)
    /// のビジネスルール 3・4）。
    ///
    pub enum ShipperType with Eq, ToString {
        case Individual
        case Corporate
    }

    ///
    /// 荷主として成立しない理由。
    ///
    /// 例外に頼らず `Result` で返す。呼び出し側は失敗を扱うことを型で強制される。
    /// **コンテキスト固有型**とし、他の BC と共有しない（`arch-lint` 規約 4）。
    ///
    pub enum ShipperError with Eq, ToString {
        case ShipperIdIsRequired
        case NameIsRequired
        case NameIsTooLong
        case EmailIsRequired
        case EmailIsMalformed
        case AddressIsTooLong
        case PhoneIsTooLong
        case ContractNumberIsRequired
        case ContractNumberIsTooLong
        case DiscountRateOutOfRange
    }

    ///
    /// 荷主（集約ルート）。
    ///
    /// 割引率は**万分率の整数**（`3000` = 30.00%）で保持する。`NUMERIC(5,4)` に対応し、
    /// 浮動小数点の誤差を金額計算へ持ち込まない。
    ///
    pub enum Shipper({
        shipperId = String,
        shipperCode = String,
        name = String,
        email = String,
        phone = Option[String],
        address = Option[String],
        shipperType = ShipperType,
        contractNumber = Option[String],
        discountRate = Option[Int32]
    })

    // ---- 生成（ファクトリ） ----

    ///
    /// 個人荷主を登録する。
    ///
    /// `shipperId` は呼び出し側（アプリケーション層）が生成した UUID の文字列表現。
    /// **ドメイン層で UUID を生成しない**。生成は効果であり、集約は効果を要求しない。
    ///
    pub def registerIndividual(shipperId: String, name: String, email: String,
                               phone: Option[String], address: Option[String]):
            Result[ShipperError, Shipper] =
        forM (
            id    <- requireShipperId(shipperId);
            n     <- requireName(name);
            e     <- requireEmail(email);
            p     <- checkPhone(phone);
            a     <- checkAddress(address)
        ) yield Shipper.Shipper({
            shipperId = id,
            shipperCode = generateCode(id),
            name = n,
            email = e,
            phone = p,
            address = a,
            shipperType = ShipperType.Individual,
            contractNumber = None,
            discountRate = None
        })

    ///
    /// 法人荷主を登録する。
    ///
    /// 契約番号と割引率は**必須**である（ビジネスルール 3）。
    /// 欠けている場合に個人へ倒さないのは、法人として登録したのに割引が効かない
    /// 荷主が静かに生まれるのを防ぐためである。
    ///
    pub def registerCorporate(shipperId: String, name: String, email: String,
                              phone: Option[String], address: Option[String],
                              contractNumber: String, discountRate: Int32):
            Result[ShipperError, Shipper] =
        forM (
            id    <- requireShipperId(shipperId);
            n     <- requireName(name);
            e     <- requireEmail(email);
            p     <- checkPhone(phone);
            a     <- checkAddress(address);
            c     <- requireContractNumber(contractNumber);
            d     <- requireDiscountRate(discountRate)
        ) yield Shipper.Shipper({
            shipperId = id,
            shipperCode = generateCode(id),
            name = n,
            email = e,
            phone = p,
            address = a,
            shipperType = ShipperType.Corporate,
            contractNumber = Some(c),
            discountRate = Some(d)
        })

    ///
    /// 永続化された行から荷主を組み立てる。
    ///
    /// 保存済みの値を読み戻す経路であり、**登録時の検証をやり直さない**。
    /// 検証を通った値だけが保存されている前提に立つ。ここで再検証すると、
    /// ルールを厳しくした瞬間に既存データが読めなくなる。
    ///
    pub def reconstruct(shipperId: String, shipperCode: String, name: String, email: String,
                        phone: Option[String], address: Option[String], shipperType: ShipperType,
                        contractNumber: Option[String], discountRate: Option[Int32]): Shipper =
        Shipper.Shipper({
            shipperId = shipperId, shipperCode = shipperCode, name = name, email = email,
            phone = phone, address = address, shipperType = shipperType,
            contractNumber = contractNumber, discountRate = discountRate
        })

    // ---- 検証 ----

    def requireShipperId(value: String): Result[ShipperError, String] =
        if (String.trim(value) == "") Err(ShipperError.ShipperIdIsRequired) else Ok(value)

    def requireName(value: String): Result[ShipperError, String] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            Err(ShipperError.NameIsRequired)
        else if (String.length(trimmed) > maxNameLength())
            Err(ShipperError.NameIsTooLong)
        else
            Ok(trimmed)

    ///
    /// メールアドレスの形式を検証する。
    ///
    /// RFC への厳密な準拠は目指さない（正規表現で完全には表現できない）。
    /// **明らかに誤っている入力を弾いて打ち間違いに気付かせる**のが目的である。
    /// 到達可能性の確認は本システムの範囲外（メール送信基盤を持たない）。
    ///
    def requireEmail(value: String): Result[ShipperError, String] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            Err(ShipperError.EmailIsRequired)
        else if (not isEmailShaped(trimmed) or String.length(trimmed) > maxEmailLength())
            Err(ShipperError.EmailIsMalformed)
        else
            Ok(trimmed)

    /// `local@domain.tld` の形をしているか
    def isEmailShaped(value: String): Bool =
        if (String.contains(substr = " ", value))
            false
        else
            match String.indexOf(substr = "@", value) {
                case None    => false
                case Some(i) =>
                    let local = String.slice(start = 0, end = i, value);
                    let domain = String.slice(start = i + 1, end = String.length(value), value);
                    // `@` が 2 つ以上ある場合はドメイン側に残るため弾ける
                    local != "" and not String.contains(substr = "@", domain)
                        and String.contains(substr = ".", domain)
                        and not String.startsWith(prefix = ".", domain)
                        and not String.endsWith(suffix = ".", domain)
            }

    def checkPhone(value: Option[String]): Result[ShipperError, Option[String]] =
        checkOptional(value, maxPhoneLength(), ShipperError.PhoneIsTooLong)

    def checkAddress(value: Option[String]): Result[ShipperError, Option[String]] =
        checkOptional(value, maxAddressLength(), ShipperError.AddressIsTooLong)

    ///
    /// 任意項目の長さを検証する。
    ///
    /// 空文字は「未入力」として `None` に倒す。フォームは未入力を空文字で送るため、
    /// ここで揃えないと「空文字が入った荷主」と「未入力の荷主」が DB に混在する。
    ///
    def checkOptional(value: Option[String], maxLength: Int32,
                      tooLong: ShipperError): Result[ShipperError, Option[String]] =
        match value {
            case None => Ok(None)
            case Some(raw) =>
                let trimmed = String.trim(raw);
                if (trimmed == "")
                    Ok(None)
                else if (String.length(trimmed) > maxLength)
                    Err(tooLong)
                else
                    Ok(Some(trimmed))
        }

    def requireContractNumber(value: String): Result[ShipperError, String] =
        let trimmed = String.trim(value);
        if (trimmed == "")
            Err(ShipperError.ContractNumberIsRequired)
        else if (String.length(trimmed) > maxContractNumberLength())
            Err(ShipperError.ContractNumberIsTooLong)
        else
            Ok(trimmed)

    ///
    /// 割引率を検証する（ビジネスルール 4）。
    ///
    /// 負の値を弾くのは、割引ではなく**割増**になってしまうためである。
    ///
    def requireDiscountRate(value: Int32): Result[ShipperError, Int32] =
        if (value < 0 or value > maxDiscountRate())
            Err(ShipperError.DiscountRateOutOfRange)
        else
            Ok(value)

    // ---- 荷主コード ----

    ///
    /// 荷主コードを生成する（ビジネスルール 5）。
    ///
    /// `SHP-` + `ShipperId` の先頭 8 文字（大文字）。ID から決まるため、
    /// 同じ荷主には常に同じコードが付く。利用者に見えるのはこのコードである。
    ///
    pub def generateCode(shipperId: String): String =
        let head = if (String.length(shipperId) >= 8)
                       String.slice(start = 0, end = 8, shipperId)
                   else
                       shipperId;
        "SHP-" + String.toUpperCase(head)

    // ---- 表示 ----

    ///
    /// 割引率を百分率の文字列へ変換する。
    ///
    /// 内部表現は万分率のため、そのまま画面に出すと `1500%` に見える。
    ///
    pub def discountRatePercent(basisPoints: Int32): String =
        let whole = basisPoints / 100;
        let fraction = basisPoints - whole * 100;
        let padded = if (fraction < 10) "0${fraction}" else "${fraction}";
        "${whole}.${padded}"

    // ---- 永続化値との対応 ----

    /// 荷主種別を永続化値へ
    pub def shipperTypeToPersisted(t: ShipperType): String = match t {
        case ShipperType.Individual => "INDIVIDUAL"
        case ShipperType.Corporate  => "CORPORATE"
    }

    ///
    /// 永続化値から荷主種別へ。
    ///
    /// 未知の値は `None` に倒す。既定値（個人）へ倒すと、DB の値が壊れたときに
    /// **法人が個人として扱われ割引が静かに消える**。
    ///
    pub def shipperTypeFromPersisted(value: String): Option[ShipperType] = match value {
        case "INDIVIDUAL" => Some(ShipperType.Individual)
        case "CORPORATE"  => Some(ShipperType.Corporate)
        case _            => None
    }

    /// 荷主種別の表示名
    pub def shipperTypeLabel(t: ShipperType): String = match t {
        case ShipperType.Individual => "個人"
        case ShipperType.Corporate  => "法人"
    }

    // ---- 参照 ----

    pub def shipperIdOf(s: Shipper): String =
        let Shipper.Shipper(x) = s;
        x#shipperId

    pub def shipperCodeOf(s: Shipper): String =
        let Shipper.Shipper(x) = s;
        x#shipperCode

    pub def nameOf(s: Shipper): String =
        let Shipper.Shipper(x) = s;
        x#name

    pub def emailOf(s: Shipper): String =
        let Shipper.Shipper(x) = s;
        x#email

    pub def phoneOf(s: Shipper): Option[String] =
        let Shipper.Shipper(x) = s;
        x#phone

    pub def addressOf(s: Shipper): Option[String] =
        let Shipper.Shipper(x) = s;
        x#address

    pub def shipperTypeOf(s: Shipper): ShipperType =
        let Shipper.Shipper(x) = s;
        x#shipperType

    pub def contractNumberOf(s: Shipper): Option[String] =
        let Shipper.Shipper(x) = s;
        x#contractNumber

    pub def discountRateOf(s: Shipper): Option[Int32] =
        let Shipper.Shipper(x) = s;
        x#discountRate

    // ---- 制限値 ----

    /// `shipper.name` の桁数と一致させる
    def maxNameLength(): Int32 = 200

    /// `shipper.email` の桁数と一致させる
    def maxEmailLength(): Int32 = 200

    /// `shipper.phone` の桁数と一致させる
    def maxPhoneLength(): Int32 = 50

    /// `shipper.address` の桁数と一致させる
    def maxAddressLength(): Int32 = 500

    /// `shipper.contract_number` の桁数と一致させる
    def maxContractNumberLength(): Int32 = 50

    /// 割引率の上限（万分率）。30.00%
    pub def maxDiscountRate(): Int32 = 3000
}
