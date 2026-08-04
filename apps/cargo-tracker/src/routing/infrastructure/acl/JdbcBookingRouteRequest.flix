///
/// `BookingRouteRequest`（ACL）の JDBC 実装。
///
/// **`cargo` テーブルを直接引く**。Booking Context のモジュール
/// （`BookingModel` / `CargoRepo`）は参照しない。参照すると Routing が
/// Booking のモデルに依存し、`arch-lint` 規約 4 に違反する。
/// `BookingJdbcShipperExistence` と対称の構造である。
///
/// **コンテキスト間の結合を SQL の 1 文に閉じ込める**ことで、
/// ドメイン層とアプリケーション層は相手のモデルを知らずに済む。
///
mod RoutingJdbcBookingRouteRequest {
    use RoutingBookingRouteRequest.{BookingRouteRequest, RouteRequest, routeRequest}
    use RoutingModel.CargoCapability
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx

    ///
    /// 依頼を引く SQL。
    ///
    /// **取るのは経路探索に要る 5 列だけ**である。重量・寸法・危険物申告は
    /// Routing の関心ではない。「ついでに全部取る」形にすると、いずれ越境が始まる
    /// （`BookingJdbcShipperExistence.describeSql` と同じ判断）。
    ///
    /// **`booking_status` で絞るのは SQL 側**である。取ってからアプリで捨てると、
    /// 「引き渡し前の予約を読める経路」がコード上に残り、後から誰かが使う。
    ///
    def findSql(): String =
        "SELECT booking_id, origin_unlocode, destination_unlocode, " +
        "       arrival_deadline, cargo_type, weight " +
        "FROM cargo WHERE booking_id = ? AND booking_status = 'ROUTE_PROPOSED'"

    def findInDb(bookingId: String): Option[RouteRequest] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, findSql(), stmt -> {
            stmt.setString(1, String.trim(bookingId));
            let rs = stmt.executeQuery();
            let found =
                if (rs.next())
                    let cargoType = rs.getString("cargo_type");
                    let foundId = rs.getString("booking_id");
                    let origin = rs.getString("origin_unlocode");
                    let destination = rs.getString("destination_unlocode");
                    // `DATE` は `YYYY-MM-DD` として読む。時刻を付けない
                    let deadline = rs.getString("arrival_deadline");
                    // DB は NUMERIC(10,3)（キログラム）。内部表現はグラムの整数
                    let weight = rs.getBigDecimal("weight");
                    let weightGrams = weight.movePointRight(3).longValue();
                    capabilityOf(cargoType) |> Option.map(cap ->
                        routeRequest(foundId, origin, destination, deadline,
                                     cargoTypeLabel(cargoType), cap, weightGrams,
                                     tariffClassOf(cargoType)))
                else
                    None;
            rs.close();
            found
        })

    ///
    /// 貨物種別 → 対応可能貨物種別の翻訳（ACL の中心）。
    ///
    /// 「この貨物は何か」から「この航海は何を運べる必要があるか」へ写す。
    /// 値が同じ綴りなのは偶然であり、**同じ列挙を共有しない**（IT6・US24 の判断）。
    ///
    /// **未知の種別を `General` へ倒さない**。危険物を一般貨物として読むと、
    /// 危険物を運べない航海が候補として提示される。**読めない値は依頼として
    /// 成立させない**（`None` を返し、画面は「作業できない」を示す）。
    /// IT6 で温度単位について同じ判断をした——未知の単位を既定値へ倒すと
    /// 華氏 -13°F が摂氏 -13°C になり、冷凍と冷蔵が入れ替わる。
    ///
    def capabilityOf(cargoType: String): Option[CargoCapability] = match cargoType {
        case "GENERAL"      => Some(CargoCapability.General)
        case "HAZARDOUS"    => Some(CargoCapability.Hazardous)
        case "REFRIGERATED" => Some(CargoCapability.Refrigerated)
        case _              => None
    }

    ///
    /// 貨物種別 → 運賃区分の翻訳。
    ///
    /// **`CargoCapability` から導かない**。「運べるか」と「いくらか」は
    /// 別の判断であり、いまは 1 対 1 に見えても、いずれ対応が崩れる
    /// （たとえば「精密機器」は専用の船腹が要るが標準運賃かもしれない）。
    ///
    /// 未知の種別はここへ来ない（`capabilityOf` が先に `None` を返す）。
    /// それでも `Standard` へ倒さず、**危険物の割増が落ちない**ように
    /// 網羅的に書く。
    ///
    def tariffClassOf(cargoType: String): SharedFreightTariff.TariffClass =
        match cargoType {
            case "HAZARDOUS"    => SharedFreightTariff.TariffClass.Hazardous
            case "REFRIGERATED" => SharedFreightTariff.TariffClass.Refrigerated
            case _              => SharedFreightTariff.TariffClass.Standard
        }

    /// 画面に出す貨物種別の表示名（利用者に列挙値を見せない）
    def cargoTypeLabel(cargoType: String): String = match cargoType {
        case "HAZARDOUS"    => "危険物"
        case "REFRIGERATED" => "冷凍・冷蔵"
        case _              => "一般貨物"
    }

    /// ACL ポートに JDBC 実装を注入する
    pub def withJdbcBookingRouteRequest(f: Unit -> a \ ef + BookingRouteRequest):
            a \ (ef - BookingRouteRequest) + Tx + IO =
        run {
            f()
        } with handler BookingRouteRequest {
            def findRouteRequest(bookingId, k) = k(findInDb(bookingId))
        }
}
