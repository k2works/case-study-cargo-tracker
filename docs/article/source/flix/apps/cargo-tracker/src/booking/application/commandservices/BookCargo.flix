///
/// 貨物予約のユースケース（US04）。
///
/// **判断の順序に意味がある**。荷主の存在確認 → 入力の検証 → 保存の順に進む。
///
/// 荷主の確認を先に置くのは、**存在しない荷主 ID での予約を外部キー違反ではなく
/// 入力エラーとして返す**ためである（US04 受入条件 7 の壊れ方）。Booking と Shipper は
/// 別コンテキストであり DB 外部キーを張らないため、ここで確認しなければ
/// 存在しない荷主に紐づく貨物が生まれる。
///
mod BookingBookCargo {
    use BookingModel.{Cargo, CargoType, BookingError}
    use BookingModel.{bookCargo, generateBookingId}
    use CargoRepo.CargoRepo
    use SharedClock.Clock
    use SharedIdGen.IdGen
    use SharedLocationCatalog.LocationCatalog
    use ShipperExistenceChecker.ShipperExistenceChecker

    ///
    /// 予約の指示。
    ///
    /// 画面から届く**文字列のまま**受け取る（重量・個数・寸法も含む）。
    /// 数値へ変換できない入力をそのまま画面へ戻す必要があるためである。
    ///
    pub enum BookCommand({
        shipperCode = String,
        origin = String,
        destination = String,
        arrivalDeadline = String,
        cargoType = CargoType,
        weightGrams = Int64,
        dimensions = Option[(Int32, Int32, Int32)],
        quantity = Option[Int32],
        description = Option[String],
        hazardousDeclaration = Option[BookingModel.HazardousDeclaration],
        temperatureRequirement = Option[BookingModel.TemperatureRequirement]
    })

    /// 予約の結果
    pub enum BookResult {
        case Booked(Cargo)
        case Invalid(BookingError)
    }

    /// 結果の種別名（テストの失敗メッセージで何が返ったかを示すため）
    pub def resultLabel(r: BookResult): String = match r {
        case BookResult.Booked(_)  => "Booked"
        case BookResult.Invalid(e) => "Invalid(${e})"
    }

    /// 予約の指示を組み立てる
    pub def command(shipperCode: String, origin: String, destination: String,
                    arrivalDeadline: String, cargoType: CargoType, weightGrams: Int64,
                    dimensions: Option[(Int32, Int32, Int32)], quantity: Option[Int32],
                    description: Option[String],
                    hazardousDeclaration: Option[BookingModel.HazardousDeclaration],
                    temperatureRequirement: Option[BookingModel.TemperatureRequirement]):
            BookCommand =
        BookCommand.BookCommand({
            shipperCode = shipperCode, origin = origin, destination = destination,
            arrivalDeadline = arrivalDeadline, cargoType = cargoType,
            weightGrams = weightGrams, dimensions = dimensions, quantity = quantity,
            description = description, hazardousDeclaration = hazardousDeclaration,
            temperatureRequirement = temperatureRequirement
        })

    ///
    /// 貨物を予約する。
    ///
    /// **順序**:
    ///
    /// 1. 荷主コードから荷主 ID を解決する（ACL 経由。外部キー違反の 500 を防ぐ）
    /// 2. 入力を検証して集約を作る
    /// 3. **出発地・目的地がマスタに存在するか確認する**（同じく外部キー違反を防ぐ）
    /// 4. 保存する
    ///
    /// 港の確認を集約の生成より後に置くのは、**形式の誤り（5 文字でない）と
    /// 取扱の可否（マスタにない）は利用者にとって別の問題**だからである。
    /// 形式が壊れている入力でマスタを引いても意味がない。
    ///
    pub def book(cmd: BookCommand):
            BookResult \ CargoRepo + IdGen + ShipperExistenceChecker + LocationCatalog + Clock =
        let BookCommand.BookCommand(c) = cmd;
        if (String.trim(c#shipperCode) == "")
            BookResult.Invalid(BookingError.ShipperIdIsRequired)
        else
            match ShipperExistenceChecker.resolveShipperId(c#shipperCode) {
                case None => BookResult.Invalid(BookingError.ShipperNotFound)
                case Some(shipperId) => bookResolved(cmd, shipperId)
            }

    /// 荷主が確認できた後の予約処理
    def bookResolved(cmd: BookCommand, shipperId: String):
            BookResult \ CargoRepo + IdGen + LocationCatalog + Clock =
        let BookCommand.BookCommand(c) = cmd;
        let bookingId = generateBookingId(IdGen.newId());
        match bookCargo(bookingId, shipperId, c#origin, c#destination,
                            c#arrivalDeadline, Clock.today(), c#cargoType, c#weightGrams,
                        c#dimensions, c#quantity, c#description,
                        c#hazardousDeclaration, c#temperatureRequirement) {
            case Err(e) => BookResult.Invalid(e)
            case Ok(cargo) => saveIfLocationsAvailable(cargo)
        }

    ///
    /// 取扱可能な港であることを確認してから保存する。
    ///
    /// **壊れ方**: `cargo.origin_unlocode` / `destination_unlocode` は `location` への
    /// 外部キーである。ここで確認しないと、形式の正しい未知のコード（`KRPUS` など）が
    /// 検証を通り、保存時に外部キー違反となる。トランザクションは例外を捕捉しないため、
    /// 利用者には応答が返らない。
    ///
    def saveIfLocationsAvailable(cargo: Cargo): BookResult \ CargoRepo + LocationCatalog =
        let available = LocationCatalog.all() |> List.map(fst);
        let spec = BookingModel.routeSpecificationOf(cargo);
        let origin = BookingModel.originOf(spec);
        let destination = BookingModel.destinationOf(spec);
        if (not List.memberOf(origin, available) or not List.memberOf(destination, available))
            BookResult.Invalid(BookingError.LocationIsNotAvailable)
        else {
            CargoRepo.save(cargo);
            BookResult.Booked(cargo)
        }

    /// 貨物を一覧する
    pub def listAll(): List[Cargo] \ CargoRepo =
        CargoRepo.findAll()

    ///
    /// 利用者が**見てよい予約**を一覧する。
    ///
    /// 荷主ロールは自分の予約だけ、それ以外は全件。
    /// **可視範囲の判定をここに置く**のは、画面ごとに書くと片方だけ忘れるためである
    /// （IT5 では一覧に絞り込みが無く、荷主が他社の予約を読めた）。
    ///
    /// 荷主に紐づかない荷主ロールの利用者は**0 件**とする。
    /// 全件へ倒すと、紐付けを忘れた利用者に全社の予約が見える。
    ///
    /// 絞り込みは語・状態・荷主名の 3 軸（IT8・TS14）。詳しくは `queryOf` を参照
    pub def listVisible(viewer: Option[SharedSecurity.Principal], keyword: String,
                        statusFilter: String, shipperName: String):
            List[Cargo] \ CargoRepo + ShipperExistenceChecker =
        match visibleShipperOf(viewer) {
            // 絞り込みを組み立てる前に返す。**ACL を呼ばずに済む**
            case VisibleScope.Nothing      => Nil
            case VisibleScope.All          =>
                CargoRepo.search(None, queryOf(keyword, statusFilter, shipperName))
            case VisibleScope.Owned(owner) =>
                CargoRepo.search(Some(owner), queryOf(keyword, statusFilter, shipperName))
        }

    ///
    /// 画面から届いた 3 つの文字列を絞り込み条件に変える。
    ///
    /// 荷主名だけは**この場で解決できない**。名前は Shipper Context のもので
    /// あり、ACL（`findShipperIdsByName`）で荷主 ID の集合へ翻訳してから
    /// 予約を絞る。翻訳をここに置くのは、**該当なしを 0 件として扱う判断**が
    /// 業務の判断だからである（アダプタに置くと SQL の都合で変わりうる）。
    ///
    def queryOf(keyword: String, statusFilter: String, shipperName: String):
            BookingModel.CargoQuery \ ShipperExistenceChecker =
        let shipperIds =
            if (String.trim(shipperName) == "")
                None
            else
                Some(ShipperExistenceChecker.findShipperIdsByName(String.trim(shipperName)));
        BookingModel.cargoQuery(keyword, BookingModel.parseStatusFilter(statusFilter), shipperIds)

    ///
    /// 利用者が**見てよい 1 件**を引く。
    ///
    /// 他人の予約は `None` を返す。**403 ではなく「無い」として扱う**のは、
    /// 403 だと「その予約は存在する」ことが分かるためである（観点表の軸 6-3）。
    ///
    pub def findVisible(viewer: Option[SharedSecurity.Principal], bookingId: String):
            Option[Cargo] \ CargoRepo =
        visibleOf(viewer, CargoRepo.findByBookingId(bookingId))

    ///
    /// 引いた 1 件を、見てよい範囲で絞る。
    ///
    /// **判定はここ 1 箇所にある**（IT10・TS17。IT9 レビュー M7）。
    /// IT9 までは `findVisible` と `findVisibleWithItinerary` が同じ 4 ケースの
    /// `match` を複製しており、コメントは「判定を書き写さない」と書きながら
    /// **実体は書き写していた**。片方だけ直すと、詳細だけ他人の予約が見える。
    ///
    /// 取得を引数で受け取る形にすることで、取り方（旅程を含むか）だけが違う
    /// 2 つの入口が同じ判定を通る。
    ///
    def visibleOf(viewer: Option[SharedSecurity.Principal],
                  fetched: Option[Cargo]): Option[Cargo] =
        match (visibleShipperOf(viewer), fetched) {
            case (VisibleScope.Nothing, _)            => None
            case (_, None)                            => None
            case (VisibleScope.All, found)            => found
            case (VisibleScope.Owned(owner), Some(c)) =>
                if (BookingModel.shipperIdOf(c) == owner) Some(c) else None
        }

    /// 見える範囲
    enum VisibleScope {
        /// 全件（営業担当者・経路設計者など）
        case All
        /// 特定の荷主のものだけ
        case Owned(String)
        /// 何も見えない（未認証・紐付けの無い荷主ロール）
        case Nothing
    }

    def visibleShipperOf(viewer: Option[SharedSecurity.Principal]): VisibleScope =
        match viewer {
            case None => VisibleScope.Nothing
            case Some(p) =>
                if (SharedSecurity.role(p) == SharedSecurity.Role.Shipper)
                    match SharedSecurity.shipperIdOf(p) {
                        case Some(owner) => VisibleScope.Owned(owner)
                        case None        => VisibleScope.Nothing
                    }
                else
                    VisibleScope.All
        }

    ///
    /// 旅程まで含めて**見てよい 1 件**を引く（US11・IT9）。
    ///
    /// 可視範囲の判定は `findVisible` と同じ関数（`visibleOf`）を通る。
    /// 違うのは**取り方だけ**である（旅程を含めて引くか否か）。
    ///
    pub def findVisibleWithItinerary(viewer: Option[SharedSecurity.Principal],
                                     bookingId: String): Option[Cargo] \ CargoRepo =
        visibleOf(viewer, CargoRepo.findWithItinerary(bookingId))

    /// 予約番号で 1 件引く
    pub def findByBookingId(bookingId: String): Option[Cargo] \ CargoRepo =
        CargoRepo.findByBookingId(bookingId)
}
