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
    pub def withFixedClock(fixedMillis: Int64, f: Unit -> a \ ef + Clock): a \ ef - Clock =
        run {
            f()
        } with handler Clock {
            def nowMillis(k) = k(fixedMillis)
        }

    ///
    /// 呼ばれるたびに進む時計。
    ///
    /// 「アクセスするたびに失効時刻が延びる」（最終アクセスからのタイムアウト）を
    /// 検証するには、**同一テストの中で時刻が変わる**必要がある。
    ///
    pub def withAdvancingClock(rc: Region[r], startMillis: Int64, stepMillis: Int64,
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
        }

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
            };
            (result, Ref.get(store))
        }
}

