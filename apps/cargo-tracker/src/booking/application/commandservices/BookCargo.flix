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
            BookResult \ CargoRepo + IdGen + ShipperExistenceChecker + LocationCatalog =
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
            BookResult \ CargoRepo + IdGen + LocationCatalog =
        let BookCommand.BookCommand(c) = cmd;
        let bookingId = generateBookingId(IdGen.newId());
        match bookCargo(bookingId, shipperId, c#origin, c#destination,
                            c#arrivalDeadline, c#cargoType, c#weightGrams,
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

    /// 予約番号で 1 件引く
    pub def findByBookingId(bookingId: String): Option[Cargo] \ CargoRepo =
        CargoRepo.findByBookingId(bookingId)
}
