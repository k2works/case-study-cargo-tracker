namespace CargoTracker.Web

open System
open System.Data
open Donald

// 開発用シードデータ。users テーブルが空のときのみ、ロール別の既定ユーザーを投入する（冪等）。
// 本番では別途ユーザー管理を行う前提のため、空でない場合は何もしない。

module Seed =

    /// ログイン画面に事前入力する既定ユーザー。
    [<Literal>]
    let DefaultUsername = "sales"

    /// 既定ユーザーの共通パスワード（開発用）。
    [<Literal>]
    let DefaultPassword = "password"

    /// (ユーザー名, ロール) の既定シード。1 ユーザー 1 ロール。
    let private defaultUsers =
        [ "sales", "ROLE_SALES"
          "designer", "ROLE_ROUTE_DESIGNER"
          "tracker", "ROLE_TRACKER"
          "handler", "ROLE_HANDLER"
          "billing", "ROLE_BILLING"
          "admin", "ROLE_ADMIN" ]

    /// ログイン画面のユーザー選択に使う既定ユーザー名一覧。
    let defaultUsernames = defaultUsers |> List.map fst

    /// users が空なら既定ユーザーを投入する。now は監査タイムスタンプ（ISO 8601）。
    let ensureDefaultUsers (conn: IDbConnection) (now: string) : unit =
        let count =
            conn
            |> Db.newCommand "SELECT COUNT(*) AS c FROM users"
            |> Db.querySingle (fun rd -> rd.ReadInt32 "c")
            |> Option.defaultValue 0

        if count = 0 then
            let hash = Auth.Password.hash DefaultPassword

            for username, role in defaultUsers do
                conn
                |> Db.newCommand
                    """
                    INSERT INTO users (username, email, password, enabled, created_at)
                    VALUES (@u, @e, @p, @enabled, @now)
                    """
                |> Db.setParams
                    [ "u", SqlType.String username
                      "e", SqlType.String(sprintf "%s@example.com" username)
                      "p", SqlType.String hash
                      "enabled", SqlType.Boolean true
                      "now", SqlType.String now ]
                |> Db.exec

                let userId =
                    conn
                    |> Db.newCommand "SELECT id AS id FROM users WHERE username = @u"
                    |> Db.setParams [ "u", SqlType.String username ]
                    |> Db.querySingle (fun rd -> rd.ReadInt64 "id")
                    |> Option.defaultValue 0L

                conn
                |> Db.newCommand "INSERT INTO user_roles (user_id, role) VALUES (@id, @role)"
                |> Db.setParams [ "id", SqlType.Int64 userId; "role", SqlType.String role ]
                |> Db.exec

    /// 全業務（見積→予約→経路設計→確定→追跡→荷役→例外→料金算出→精算→返金）を一通り
    /// 実行できる開発用サンプルデータを投入する（shipper が空のときのみ・冪等）。
    /// 荷主・航海・割引ポリシーのマスタと、ライフサイクル各段階の貨物（経路設計待ち／確定済み輸送中／
    /// 配送完了）を用意し、各ロールが自分の画面で操作対象を持てるようにする。
    let ensureBusinessData (conn: IDbConnection) (now: DateTimeOffset) : unit =
        let count =
            conn
            |> Db.newCommand "SELECT COUNT(*) AS c FROM shipper"
            |> Db.querySingle (fun rd -> rd.ReadInt32 "c")
            |> Option.defaultValue 0

        if count = 0 then
            // 実クロックからの相対日付。割引ポリシーは現在有効、航海は近未来、到着期限はさらに先に置く。
            let iso (d: DateTimeOffset) = d.UtcDateTime.ToString("o")
            let day (d: DateTimeOffset) = d.ToString("yyyy-MM-dd")
            let nowStr = iso now
            let past6 = day (now.AddMonths(-6))
            let future6 = day (now.AddMonths(6))
            let dep1 = iso (now.AddDays(3.0))
            let arr1 = iso (now.AddDays(10.0))
            let dep2 = iso (now.AddDays(11.0))
            let arr2 = iso (now.AddDays(20.0))
            let dep3 = iso (now.AddDays(3.0))
            let arr3 = iso (now.AddDays(25.0))
            let deadline = day (now.AddDays(40.0))

            // 荷主 UUID（cargo.shipper_id = shipper.shipper_uuid で BC-ACL 解決）。
            let u1 = "11111111-1111-1111-1111-111111111111"
            let u2 = "22222222-2222-2222-2222-222222222222"
            let u3 = "33333333-3333-3333-3333-333333333333"

            let sql =
                sprintf
                    """
                    -- 荷主（法人 2・個人 1）
                    INSERT INTO shipper (shipper_code, shipper_type, name, email, phone, contract_number, discount_rate, shipper_uuid, created_at, updated_at, version) VALUES
                        ('SHP-0001', 'CORPORATE', '山田物流株式会社', 'yamada@example.com', '03-1111-1111', 'C-1001', 0.10, '%s', '%s', '%s', 0),
                        ('SHP-0002', 'INDIVIDUAL', '佐藤太郎', 'sato@example.com', '090-2222-2222', NULL, 0, '%s', '%s', '%s', 0),
                        ('SHP-0003', 'CORPORATE', '鈴木商事株式会社', 'suzuki@example.com', '06-3333-3333', 'C-1002', 0.15, '%s', '%s', '%s', 0);

                    -- 航海（対応貨物種別つき）と寄港区間
                    INSERT INTO voyage (voyage_number, vessel_name, carrier_name, supported_cargo_types, created_at, updated_at, version) VALUES
                        ('V001', 'Pacific Star', 'Ocean Line', 'GENERAL,HAZARDOUS,REFRIGERATED', '%s', '%s', 0),
                        ('V002', 'Atlantic Wave', 'Blue Carrier', 'GENERAL,REFRIGERATED', '%s', '%s', 0);
                    INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date, seq_number, created_at, updated_at) VALUES
                        ((SELECT id FROM voyage WHERE voyage_number='V001'), 'JPTYO', 'SGSIN', '%s', '%s', 1, '%s', '%s'),
                        ((SELECT id FROM voyage WHERE voyage_number='V001'), 'SGSIN', 'USLAX', '%s', '%s', 2, '%s', '%s'),
                        ((SELECT id FROM voyage WHERE voyage_number='V002'), 'JPTYO', 'NLRTM', '%s', '%s', 1, '%s', '%s');

                    -- 割引ポリシーマスタ（現在有効）
                    INSERT INTO discount_policy (policy_type, discount_rate, applicable_condition, effective_from, effective_to, active, created_at, updated_at) VALUES
                        ('CORPORATE_STANDARD', 0.10, '法人標準契約', '%s', NULL, 1, '%s', '%s'),
                        ('VOLUME_DISCOUNT', 0.15, '請求 100 万円以上', '%s', NULL, 1, '%s', '%s'),
                        ('SEASONAL', 0.05, '季節キャンペーン', '%s', '%s', 1, '%s', '%s');

                    -- 見積（US01・料金試算の対象）
                    INSERT INTO estimate (estimate_id, origin_unlocode, destination_unlocode, arrival_deadline, cargo_type, weight_kg, status, created_at, updated_at) VALUES
                        ('EST-0001', 'JPTYO', 'USLAX', '%s', 'GENERAL', 500, 'CREATED', '%s', '%s');

                    -- 貨物（ライフサイクル各段階）
                    -- BKG-1001: 経路設計待ち（ROUTING_REQUESTED・US09-13 のデモ対象）
                    INSERT INTO cargo (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode, arrival_deadline, booking_status, created_at, updated_at, version) VALUES
                        ('BKG-1001', '%s', 'GENERAL', 500, 'JPTYO', 'USLAX', '%s', 'ROUTING_REQUESTED', '%s', '%s', 0);
                    -- BKG-1002: 確定済み・輸送中（CONFIRMED・追跡/荷役のデモ対象・冷凍）
                    INSERT INTO cargo (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode, arrival_deadline, booking_status, min_temperature, max_temperature, temperature_unit, created_at, updated_at, version) VALUES
                        ('BKG-1002', '%s', 'REFRIGERATED', 300, 'JPTYO', 'NLRTM', '%s', 'CONFIRMED', -20, -5, 'CELSIUS', '%s', '%s', 0);
                    -- BKG-1003: 配送完了（DELIVERED・料金算出/精算のデモ対象・法人）
                    INSERT INTO cargo (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode, arrival_deadline, booking_status, created_at, updated_at, version) VALUES
                        ('BKG-1003', '%s', 'GENERAL', 800, 'JPTYO', 'USLAX', '%s', 'DELIVERED', '%s', '%s', 0);

                    -- 旅程（leg・CONFIRMED/DELIVERED の集約再構成に必要）
                    INSERT INTO leg (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode, load_time, unload_time, seq_number, created_at, updated_at) VALUES
                        ((SELECT id FROM cargo WHERE booking_id='BKG-1002'), 'V002', 'JPTYO', 'NLRTM', '%s', '%s', 1, '%s', '%s'),
                        ((SELECT id FROM cargo WHERE booking_id='BKG-1003'), 'V001', 'JPTYO', 'SGSIN', '%s', '%s', 1, '%s', '%s'),
                        ((SELECT id FROM cargo WHERE booking_id='BKG-1003'), 'V001', 'SGSIN', 'USLAX', '%s', '%s', 2, '%s', '%s');

                    -- 追跡（US14/US18・照会対象）
                    INSERT INTO tracking_activity (tracking_number, booking_id, transport_status, access_token, created_at, updated_at, version) VALUES
                        ('TRK-1002', 'BKG-1002', 'ONBOARD_CARRIER', 'token-1002-demo', '%s', '%s', 0),
                        ('TRK-1003', 'BKG-1003', 'CLAIMED', 'token-1003-demo', '%s', '%s', 0);

                    -- 荷役実績（US15/US16・BKG-1003 の受入〜引取）
                    INSERT INTO handling_activity (booking_id, event_type, event_completion_time, location_unlocode, voyage_number, operator_name, created_at, updated_at, version) VALUES
                        ('BKG-1003', 'RECEIVE', '%s', 'JPTYO', 'V001', '港湾作業員A', '%s', '%s', 0),
                        ('BKG-1003', 'LOAD', '%s', 'JPTYO', 'V001', '港湾作業員A', '%s', '%s', 0);

                    -- 輸送例外（US19・未解決の遅延。料金算出の例外時料金調整のデモにも使う）
                    INSERT INTO tracking_exception_event (tracking_id, exception_type, location_unlocode, occurred_at, escalation_flag, description, resolved_at, resolution_notes, seq_number, created_at, updated_at) VALUES
                        ((SELECT id FROM tracking_activity WHERE tracking_number='TRK-1003'), 'DELAYED', 'SGSIN', '%s', 0, '荒天による寄港遅延', NULL, NULL, 1, '%s', '%s');
                    """
                    // shippers (u1,now,now / u2 / u3)
                    u1
                    nowStr
                    nowStr
                    u2
                    nowStr
                    nowStr
                    u3
                    nowStr
                    nowStr
                    // voyages
                    nowStr
                    nowStr
                    nowStr
                    nowStr
                    // carrier_movements V001-CM1, V001-CM2, V002-CM1
                    dep1
                    arr1
                    nowStr
                    nowStr
                    dep2
                    arr2
                    nowStr
                    nowStr
                    dep3
                    arr3
                    nowStr
                    nowStr
                    // discount policies
                    past6
                    nowStr
                    nowStr
                    past6
                    nowStr
                    nowStr
                    past6
                    future6
                    nowStr
                    nowStr
                    // estimate
                    deadline
                    nowStr
                    nowStr
                    // cargo BKG-1001
                    u1
                    deadline
                    nowStr
                    nowStr
                    // cargo BKG-1002
                    u2
                    deadline
                    nowStr
                    nowStr
                    // cargo BKG-1003
                    u3
                    deadline
                    nowStr
                    nowStr
                    // legs (BKG-1002, BKG-1003 x2)
                    dep3
                    arr3
                    nowStr
                    nowStr
                    dep1
                    arr1
                    nowStr
                    nowStr
                    dep2
                    arr2
                    nowStr
                    nowStr
                    // tracking_activity
                    nowStr
                    nowStr
                    nowStr
                    nowStr
                    // handling_activity x2
                    arr1
                    nowStr
                    nowStr
                    dep1
                    nowStr
                    nowStr
                    // exception
                    arr1
                    nowStr
                    nowStr

            use cmd = conn.CreateCommand()
            cmd.CommandText <- sql
            cmd.ExecuteNonQuery() |> ignore
