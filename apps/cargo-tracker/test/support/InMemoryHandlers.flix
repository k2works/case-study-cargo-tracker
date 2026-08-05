///
/// テスト用のインメモリ効果ハンドラ。
///
/// モックライブラリを使わず、効果ハンドラをテストダブルとして用いる（ADR-0002）。
/// 本番の JDBC ハンドラと同じ効果を実装するため、アプリケーション層のコードは一切変わらない。
///
mod InMemoryHandlers {
    use SharedClock.Clock
    use SharedIdGen.IdGen
    use ShipperModel.Shipper
    use TrackingReadDb.ReadDb
    use TrackingView.TrackingDetailView

    ///
    /// 時刻を固定する。
    ///
    /// これがあって初めて「30 分後にセッションが切れる」を**待たずに**検証できる。
    ///
    /// `today` も**スタブ値として明示的に受け取る**。ミリ秒から導出すると
    /// テストが `IO` を要求することになり、純粋なままで書けなくなる。
    /// 両方をテストが宣言する形にし、食い違いはテストの責任とする。
    pub def withFixedClock(fixedMillis: Int64, todayDate: String,
                           f: Unit -> a \ ef + Clock): a \ ef - Clock =
        run {
            f()
        } with handler Clock {
            def nowMillis(k) = k(fixedMillis)
            def today(k) = k(todayDate)
        }

    ///
    /// 呼ばれるたびに進む時計。
    ///
    /// 「アクセスするたびに失効時刻が延びる」（最終アクセスからのタイムアウト）を
    /// 検証するには、**同一テストの中で時刻が変わる**必要がある。
    ///
    pub def withAdvancingClock(rc: Region[r], startMillis: Int64, stepMillis: Int64,
                               todayDate: String,
                               f: Unit -> a \ ef + Clock): a \ (ef - Clock) + r =
        let current = Ref.fresh(rc, startMillis);
        run {
            f()
        } with handler Clock {
            def nowMillis(k) = {
                let value = Ref.get(current);
                Ref.put(value + stepMillis, current);
                k(value)
            }
            def today(k) = k(todayDate)
        }

    ///
    /// `ReadDb` 効果にインメモリ実装を注入する。
    ///
    /// 与えられた一覧から追跡番号で検索する。DB もサーバも起動しない。
    ///
    pub def withInMemoryReadDb(data: List[TrackingDetailView], f: Unit -> a \ ef + ReadDb): a \ ef - ReadDb =
        run {
            f()
        } with handler ReadDb {
            def findByTrackingNumber(trackingNumber, k) =
                k(List.find(v -> TrackingView.trackingNumber(v) == trackingNumber, data))
        }

    ///
    /// 貨物リポジトリのインメモリ実装。
    ///
    /// 保存後の内容を添えて返す。「保存されたか」ではなく「**何件保存されたか**」を
    /// 検証できる形にするためである。
    ///
    pub def withInMemoryCargoRepo(initial: List[BookingModel.Cargo],
                                  f: Unit -> a \ ef + CargoRepo.CargoRepo):
            (a, List[BookingModel.Cargo]) \ ef - CargoRepo.CargoRepo =
        region rc {
            let store = Ref.fresh(rc, initial);
            let result = run {
                f()
            } with handler CargoRepo.CargoRepo {
                def save(cargo, k) = {
                    Ref.put(Ref.get(store) ::: cargo :: Nil, store);
                    k()
                }
                def findByBookingId(bookingId, k) =
                    k(List.find(c -> BookingModel.bookingIdOf(c) == bookingId, Ref.get(store)))
                def findAll(k) = k(Ref.get(store))
                ///
                /// 検索。**JDBC 実装と同じ意味**（可視範囲と絞り込みを AND で重ねる）を
                /// 素朴に書く。ここが本番と食い違うと、単体で緑・本番で赤になる。
                ///
                def search(shipperId, criteria, k) =
                    let needle = String.toLowerCase(String.trim(BookingModel.queryKeywordOf(criteria)));
                    k(Ref.get(store) |> List.filter(c ->
                        (match shipperId {
                            case None     => true
                            case Some(id) => BookingModel.shipperIdOf(c) == id
                         }) and
                        // 状態。**未知の語は 0 件**（黙って広げない）
                        (match BookingModel.queryStatusOf(criteria) {
                            case BookingModel.StatusFilter.AnyStatus       => true
                            case BookingModel.StatusFilter.OnlyStatus(s)   => BookingModel.bookingStatusOf(c) == s
                            case BookingModel.StatusFilter.UnknownStatus(_) => false
                         }) and
                        // 荷主 ID の集合。**`Some(Nil)` は 0 件**（「絞らない」ではない）
                        (match BookingModel.queryShipperIdsOf(criteria) {
                            case None      => true
                            case Some(ids) => List.memberOf(BookingModel.shipperIdOf(c), ids)
                         }) and
                        (needle == "" or
                         String.contains(substr = needle,
                                         String.toLowerCase(BookingModel.bookingIdOf(c))) or
                         String.contains(substr = needle,
                                         String.toLowerCase(BookingModel.originOf(
                                             BookingModel.routeSpecificationOf(c)))) or
                         String.contains(substr = needle,
                                         String.toLowerCase(BookingModel.destinationOf(
                                             BookingModel.routeSpecificationOf(c)))))))

                ///
                /// 旅程と経路状態を保存する。**JDBC と同じく `booking_status` は触らない**。
                /// 集約ごと差し替えるが、インメモリでは列の区別が無いため
                /// 「差し替えた集約が持つ状態がそのまま入る」形になる——
                /// **JDBC 側が `booking_status` を書かないことは統合テストで確かめる**
                ///
                def saveItinerary(cargo, k) = {
                    let target = BookingModel.bookingIdOf(cargo);
                    Ref.put(Ref.get(store)
                        |> List.map(c -> if (BookingModel.bookingIdOf(c) == target) cargo else c),
                        store);
                    k()
                }
                /// インメモリは区間を別に持たないため、そのまま返す
                def findWithItinerary(bookingId, k) =
                    k(List.find(c -> BookingModel.bookingIdOf(c) == bookingId, Ref.get(store)))

                /// 追跡番号で引く（IT10・US15）
                def findByTrackingNumber(trackingNumber, k) =
                    k(List.find(c ->
                        BookingModel.trackingNumberOf(c) == Some(trackingNumber),
                        Ref.get(store)))

                /// **インメモリに行ロックは無い**。単一スレッドで動かすため
                /// 押さえるものが無く、読み取りと同じである（IT9 レビュー H2）
                def findForUpdate(bookingId, k) =
                    k(List.find(c -> BookingModel.bookingIdOf(c) == bookingId, Ref.get(store)))

                /// 追跡番号と状態を差し替える（IT10・US14）
                def saveTrackingNumber(cargo, k) = {
                    let target = BookingModel.bookingIdOf(cargo);
                    Ref.put(Ref.get(store)
                        |> List.map(c -> if (BookingModel.bookingIdOf(c) == target) cargo else c),
                        store);
                    k()
                }

                /// 同じ予約番号の要素を差し替える（登録ではなく更新）
                def updateStatus(cargo, k) = {
                    let target = BookingModel.bookingIdOf(cargo);
                    Ref.put(Ref.get(store)
                        |> List.map(c -> if (BookingModel.bookingIdOf(c) == target) cargo else c),
                        store);
                    k()
                }
            };
            (result, Ref.get(store))
        }

    ///
    /// 荷主の存在確認（ACL）のスタブ。
    ///
    /// 荷主コードと荷主 ID の対応を渡す。**Shipper Context を起動しない**ため、
    /// Booking の単体テストが Shipper の実装に引きずられない。
    ///
    pub def withStubShipperExistence(known: List[(String, String)],
                                     f: Unit -> a \ ef + ShipperExistenceChecker.ShipperExistenceChecker):
            a \ ef - ShipperExistenceChecker.ShipperExistenceChecker =
        run {
            f()
        } with handler ShipperExistenceChecker.ShipperExistenceChecker {
            def resolveShipperId(shipperCode, k) =
                k(List.findMap(pair -> {
                    let (code, id) = pair;
                    if (code == shipperCode) Some(id) else None
                }, known))
            /// 表示名はコードから機械的に作る（スタブに実データを持たせない）
            def describeShipper(shipperId, k) =
                k(List.findMap(pair -> {
                    let (code, id) = pair;
                    if (id == shipperId) Some((code, "荷主 " + code)) else None
                }, known))
            ///
            /// 名前での検索。**JDBC 実装と同じ意味**（名前とコードの部分一致）を
            /// 素朴に書く。表示名は `荷主 <コード>` であるため、コードで引けば
            /// 名前でも当たる。ここが本番と食い違うと、単体で緑・本番で赤になる。
            ///
            def findShipperIdsByName(namePart, k) =
                let needle = String.toLowerCase(String.trim(namePart));
                k(known |> List.filterMap(pair -> {
                    let (code, id) = pair;
                    let name = String.toLowerCase("荷主 " + code);
                    if (String.contains(substr = needle, String.toLowerCase(code)) or
                        String.contains(substr = needle, name))
                        Some(id)
                    else
                        None
                }))
        }

    ///
    /// 取扱可能な場所のスタブ。
    ///
    /// 既定は `location` マスタと同じ 7 件。**マスタと揃えておく**ことで、
    /// 単体テストで通った港が統合テストで弾かれる食い違いを防ぐ。
    ///
    pub def withStubLocations(known: List[(String, String)],
                              f: Unit -> a \ ef + SharedLocationCatalog.LocationCatalog):
            a \ ef - SharedLocationCatalog.LocationCatalog =
        run {
            f()
        } with handler SharedLocationCatalog.LocationCatalog {
            def all(k) = k(known)
        }

    /// `location` マスタ（`R__location_master.sql`）と同じ内容
    pub def seededLocations(): List[(String, String)] =
        ("JPTYO", "Tokyo") :: ("JPOSA", "Osaka") :: ("USLAX", "Los Angeles") ::
        ("DEHAM", "Hamburg") :: ("CNSHA", "Shanghai") :: ("HKHKG", "Hong Kong") ::
        ("SGSIN", "Singapore") :: Nil

    ///
    /// 識別子を固定する。
    ///
    /// 発行された ID を前提にしたアサーション（荷主コードが `SHP-XXXXXXXX` になる等）を
    /// 書けるようにする。乱数のままでは「何が発行されたか」を検証できない。
    ///
    pub def withFixedIds(fixedId: String, f: Unit -> a \ ef + IdGen): a \ ef - IdGen =
        run {
            f()
        } with handler IdGen {
            def newId(k) = k(fixedId)
        }

    ///
    /// 荷主リポジトリのインメモリ実装。
    ///
    /// 戻り値に**保存後の内容**を添える。「保存されたか」ではなく
    /// 「**何件保存されたか**」を検証できる形にするためである。
    /// 重複時に保存しないこと・検証失敗時に保存しないことは、件数でしか押さえられない。
    ///
    /// メールの検索は**小文字化して比較する**。JDBC 実装（`LOWER(email) = ?`）と
    /// 挙動を揃えるためであり、ここを合わせないと単体テストと統合テストで結論が変わる。
    ///
    pub def withInMemoryShipperRepo(initial: List[Shipper],
                                    f: Unit -> a \ ef + ShipperRepo.ShipperRepo):
            (a, List[Shipper]) \ ef - ShipperRepo.ShipperRepo =
        region rc {
            let store = Ref.fresh(rc, initial);
            let result = run {
                f()
            } with handler ShipperRepo.ShipperRepo {
                def save(shipper, k) = {
                    Ref.put(Ref.get(store) ::: shipper :: Nil, store);
                    k()
                }
                def findByEmail(email, k) =
                    k(List.find(s -> String.toLowerCase(ShipperModel.emailOf(s)) ==
                                     String.toLowerCase(email), Ref.get(store)))
                def findByShipperId(shipperId, k) =
                    k(List.find(s -> ShipperModel.shipperIdOf(s) == shipperId, Ref.get(store)))
                def findAll(k) = k(Ref.get(store))
                ///
                /// 絞り込み。**JDBC 実装と同じ意味**（氏名 / 社名・荷主コード・
                /// メールの部分一致、大文字小文字を区別しない）を素朴に書く。
                /// ここが本番と食い違うと、単体で緑・本番で赤になる。
                ///
                def search(keyword, k) =
                    let needle = String.toLowerCase(String.trim(keyword));
                    k(List.filter(s ->
                        String.contains(substr = needle, String.toLowerCase(ShipperModel.nameOf(s))) or
                        String.contains(substr = needle, String.toLowerCase(ShipperModel.shipperCodeOf(s))) or
                        String.contains(substr = needle, String.toLowerCase(ShipperModel.emailOf(s))),
                        Ref.get(store)))
            };
            (result, Ref.get(store))
        }
}

