///
/// 認証・認可の語彙（共有カーネル）。
///
/// ロールは業務全体で共有される概念であり、[バックエンドアーキテクチャ](../../../../../../docs/design/architecture_backend.md)
/// の「ロール設計」を正典とする。
///
/// **なぜドメイン層に置くか**: IT2 では `Role` を `SharedHttp`（HTTP 層）に、
/// `Principal` を `SharedHtmlLayout`（HTML 描画）に置いていた。この配置では、
/// 認証基盤やドメイン層がロールを扱おうとした時点で「domain → infrastructure」の
/// 依存が生じ、arch-lint 規約 1 に違反する（IT2 レビュー H10）。
/// 語彙を共有カーネルのドメイン層へ置き、HTTP・HTML の側が参照する向きに反転させる。
///
mod SharedSecurity {

    /// ロール（永続化値との対応は `roleFromPersisted` / `roleToPersisted`）
    pub enum Role with Eq, ToString {
        case Shipper
        case Consignee
        case Sales
        case Router
        case Handler
        case Tracker
        case Accountant
        case Admin
    }

    ///
    /// ログイン中の利用者。
    ///
    /// 表示名とロールのみを持ち、パスワードハッシュ等の認証情報は持たない。
    /// 画面・ログ・監査証跡へ渡る値であり、秘密を含めてはならないため。
    ///
    ///
    /// 認証済みの利用者。
    ///
    /// `shipperId` は**どの荷主に属するか**（荷主ロールのみ）。
    /// 営業担当者・経路設計者・管理者は特定の荷主に属さないため `None` になる。
    /// **認可の関心**であり、業務データの所有者を判定するために持つ。
    ///
    pub enum Principal({ userId = Int64, username = String, role = Role,
                         shipperId = Option[String] })

    ///
    /// 荷主に紐づかない利用者を組み立てる。
    ///
    /// 営業担当者・経路設計者・管理者はこちらを使う。
    ///
    pub def principal(id: Int64, name: String, r: Role): Principal =
        Principal.Principal({ userId = id, username = name, role = r, shipperId = None })

    ///
    /// 荷主との紐付けを含めて利用者を組み立てる。
    ///
    /// **`principal` を拡張せず別の関数にする**。既定を変えると、呼び出し側が
    /// 「紐付けを渡し忘れた」のか「意図的に紐づけない」のかを区別できなくなる
    /// （IT5 で `parseScaled` と `parseSignedScaled` を分けたのと同じ判断）。
    ///
    pub def principalOf(id: Int64, name: String, r: Role,
                        shipperId: Option[String]): Principal =
        Principal.Principal({ userId = id, username = name, role = r, shipperId = shipperId })

    ///
    /// 利用者が属する荷主（属さないなら `None`）。
    ///
    /// これを使って**一覧が返すデータの範囲**を絞る。認可（画面へ到達できるか）
    /// とは別の関心であり、到達した画面が返す中身を決める（観点表の軸 6）。
    ///
    pub def shipperIdOf(p: Principal): Option[String] =
        let Principal.Principal(x) = p;
        x#shipperId

    /// 利用者 ID
    pub def userId(p: Principal): Int64 =
        let Principal.Principal(x) = p;
        x#userId

    /// ログイン名（画面の表示にも用いる）
    pub def username(p: Principal): String =
        let Principal.Principal(x) = p;
        x#username

    /// ロール
    pub def role(p: Principal): Role =
        let Principal.Principal(x) = p;
        x#role

    ///
    /// 永続化値（`user_roles.role`）から `Role` へ変換する。
    ///
    /// 未知の値は `None` を返す。ロールを増やしたときに DB とコードのどちらかだけが
    /// 更新される状況は起こりうるため、**不明なロールを既定値へ倒さない**
    /// （権限のない利用者に権限を与えてしまうため）。
    ///
    pub def roleFromPersisted(value: String): Option[Role] = match value {
        case "ROLE_SHIPPER"    => Some(Role.Shipper)
        case "ROLE_CONSIGNEE"  => Some(Role.Consignee)
        case "ROLE_SALES"      => Some(Role.Sales)
        case "ROLE_ROUTER"     => Some(Role.Router)
        case "ROLE_HANDLER"    => Some(Role.Handler)
        case "ROLE_TRACKER"    => Some(Role.Tracker)
        case "ROLE_ACCOUNTANT" => Some(Role.Accountant)
        case "ROLE_ADMIN"      => Some(Role.Admin)
        case _                 => None
    }

    /// `Role` から永続化値へ変換する
    pub def roleToPersisted(r: Role): String = match r {
        case Role.Shipper    => "ROLE_SHIPPER"
        case Role.Consignee  => "ROLE_CONSIGNEE"
        case Role.Sales      => "ROLE_SALES"
        case Role.Router     => "ROLE_ROUTER"
        case Role.Handler    => "ROLE_HANDLER"
        case Role.Tracker    => "ROLE_TRACKER"
        case Role.Accountant => "ROLE_ACCOUNTANT"
        case Role.Admin      => "ROLE_ADMIN"
    }

    ///
    /// ロールの表示名（画面・ログに出す業務の言葉）。
    ///
    /// 列挙値や `ROLE_` 接頭辞をそのまま利用者に見せない（IT2 ふりかえり T8）。
    ///
    pub def roleLabel(r: Role): String = match r {
        case Role.Shipper    => "荷主"
        case Role.Consignee  => "荷受人"
        case Role.Sales      => "営業担当者"
        case Role.Router     => "経路設計者"
        case Role.Handler    => "荷役作業員"
        case Role.Tracker    => "追跡管理者"
        case Role.Accountant => "経理担当者"
        case Role.Admin      => "システム管理者"
    }

    /// 全ロール（テーブル駆動のテスト・網羅性の検査に使う）
    pub def allRoles(): List[Role] =
        Role.Shipper :: Role.Consignee :: Role.Sales :: Role.Router ::
        Role.Handler :: Role.Tracker :: Role.Accountant :: Role.Admin :: Nil
}
