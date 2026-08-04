///
/// 航海スケジュールの登録・更新（US24・US25）。
///
/// 判断はドメイン（`RoutingModel.registerVoyage`）にある。ここでの責務は
/// 「引く・呼ぶ・保存する」の順序と、失敗の伝え方だけである。
///
mod RoutingRegisterVoyage {
    use RoutingModel.{Voyage, CarrierMovement, CargoCapability, VoyageError}
    use VoyageRepo.VoyageRepo

    /// 登録の結果
    pub enum RegisterResult {
        case Registered(Voyage)
        /// 同じ航海番号が既に存在する（利用者に既存を示すため保持する）
        case Duplicate(Voyage)
        case Invalid(VoyageError)
    }

    /// 更新の結果
    pub enum UpdateResult {
        case Updated(Voyage)
        /// 指定された航海番号が存在しない
        case NotFound
        ///
        /// 読み込み時点から内容が変わっている（ロストアップデートの防止）。
        ///
        /// **現在の値を返す**。利用者は「何が変わったか」を見てから
        /// もう一度判断できる。値を返さないと、やり直す手掛かりが無い。
        ///
        case Stale(Voyage)
        case Invalid(VoyageError)
    }

    /// 結果の種別名（テストの失敗メッセージで何が返ったかを示すため）
    pub def registerLabel(r: RegisterResult): String = match r {
        case RegisterResult.Registered(_) => "Registered"
        case RegisterResult.Duplicate(_)  => "Duplicate"
        case RegisterResult.Invalid(e)    => "Invalid(${e})"
    }

    pub def updateLabel(r: UpdateResult): String = match r {
        case UpdateResult.Updated(_) => "Updated"
        case UpdateResult.NotFound   => "NotFound"
        case UpdateResult.Stale(_)   => "Stale"
        case UpdateResult.Invalid(e) => "Invalid(${e})"
    }

    ///
    /// 航海スケジュールを登録する。
    ///
    /// **重複を先に確認する**。一意制約違反を捕まえて分岐する形にすると、
    /// 利用者に見えるのは 500 になる（IT4 で荷主のメール重複について
    /// 同じ判断をした）。DB の制約は同時登録に対する最後の砦として残す。
    ///
    /// 検証を重複確認より後に置くのは、**存在しない航海番号で検証エラーを
    /// 出しても利用者が困らない**一方、重複は「別の番号にする」という
    /// 異なる行動を要求するためである。
    ///
    pub def register(voyageNumber: String, vesselName: String, carrierName: String,
                     movements: List[CarrierMovement],
                     capabilities: List[CargoCapability]): RegisterResult \ VoyageRepo =
        match RoutingModel.registerVoyage(voyageNumber, vesselName, carrierName,
                                          movements, capabilities) {
            case Err(e) => RegisterResult.Invalid(e)
            case Ok(voyage) =>
                match VoyageRepo.findByVoyageNumber(RoutingModel.voyageNumberOf(voyage)) {
                    case Some(existing) => RegisterResult.Duplicate(existing)
                    case None           => {
                        VoyageRepo.save(voyage);
                        RegisterResult.Registered(voyage)
                    }
                }
        }

    ///
    /// 航海スケジュールを更新する。
    ///
    /// `expectedVersion` は編集画面を開いた時点の内容から作った指紋である。
    /// 保存の直前に現在の内容と照合し、食い違えば `Stale` を返す。
    ///
    /// **版を持たずに上書きすると、差分確認をしている間に別の利用者が行った
    /// 更新が黙って消える**（ロストアップデート）。運航変更は複数人が同時に
    /// 触りうる情報であり、消えたことに誰も気付かない。
    ///
    pub def update(voyageNumber: String, expectedVersion: String,
                   vesselName: String, carrierName: String,
                   movements: List[CarrierMovement],
                   capabilities: List[CargoCapability]): UpdateResult \ VoyageRepo =
        // **行を押さえてから読む**。版の照合はアプリケーション側の比較であり、
        // 読む→比べる→書くの間に別のトランザクションが書けば、
        // 両方が同じ版を読んで両方が書ける（IT6 レビュー H4）
        match VoyageRepo.findForUpdate(voyageNumber) {
            case None => UpdateResult.NotFound
            case Some(current) =>
                if (versionOf(current) != expectedVersion)
                    UpdateResult.Stale(current)
                else
                    match RoutingModel.registerVoyage(voyageNumber, vesselName, carrierName,
                                                      movements, capabilities) {
                        case Err(e) => UpdateResult.Invalid(e)
                        case Ok(updated) => {
                            VoyageRepo.replace(updated);
                            UpdateResult.Updated(updated)
                        }
                    }
        }

    ///
    /// 航海の内容から版（指紋）を作る。
    ///
    /// **実体は集約にある**（IT8・TS14。IT6 レビュー M8）。版は集約の全属性から
    /// 作るものであり、属性を足す判断と版に含める判断は同じ場所で下される
    /// べきである。ここに残すのは画面・ルート層からの呼び口としてであり、
    /// **判断は持たない**。
    ///
    pub def versionOf(v: Voyage): String = RoutingModel.versionOf(v)

    /// 航海を一覧する
    pub def listAll(): List[Voyage] \ VoyageRepo =
        VoyageRepo.findAll()

    /// 航海番号で 1 件引く
    pub def findByVoyageNumber(voyageNumber: String): Option[Voyage] \ VoyageRepo =
        VoyageRepo.findByVoyageNumber(voyageNumber)

    /// 条件で検索する（US07）
    pub def search(origin: String, destination: String,
                   departureFrom: String, departureTo: String,
                   capability: Option[CargoCapability]): List[Voyage] \ VoyageRepo =
        VoyageRepo.search(origin, destination, departureFrom, departureTo, capability)
}
