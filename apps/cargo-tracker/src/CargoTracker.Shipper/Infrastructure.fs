namespace CargoTracker.Shipper.Infrastructure

open System
open System.Data
open Donald
open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain
open CargoTracker.Shipper.Application

// Shipper コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// ANSI 標準の範囲で SQL を記述し、SQLite / PostgreSQL 両方言で動作させる（ADR-0003）。

module ShipperRepository =

    /// ShipperKind を永続化用の (種別文字列, 契約番号 option, 割引率) に展開する。
    let private explodeKind (kind: ShipperKind) : string * string option * decimal =
        match kind with
        | Individual -> "INDIVIDUAL", None, 0.0000m
        | Corporate(contract, rate) -> "CORPORATE", Some(ContractNumber.value contract), DiscountRate.value rate

    /// Donald の出力ポート実装を生成する。clock は監査カラムのタイムスタンプに使う（ADR-0006）。
    let create (conn: IDbConnection) (clock: Clock) : ShipperRepository =

        let existsByEmail (email: Email) : Async<Result<bool, DomainError>> =
            async {
                try
                    let count =
                        conn
                        |> Db.newCommand "SELECT COUNT(*) AS cnt FROM shipper WHERE email = @email"
                        |> Db.setParams [ "email", SqlType.String(Email.value email) ]
                        |> Db.querySingle (fun rd -> rd.ReadInt32 "cnt")

                    return Ok(count |> Option.defaultValue 0 > 0)
                with ex ->
                    return Error(BusinessRuleViolation("ShipperRepository", ex.Message))
            }

        let save (shipper: Shipper) : Async<Result<unit, DomainError>> =
            async {
                try
                    let kindStr, contract, rate = explodeKind shipper.Kind
                    let now = clock ()

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO shipper
                            (shipper_code, shipper_type, name, email, phone,
                             contract_number, discount_rate, created_at, updated_at, version)
                        VALUES
                            (@shipper_code, @shipper_type, @name, @email, @phone,
                             @contract_number, @discount_rate, @now, @now, 0)
                        """
                    |> Db.setParams
                        [ "shipper_code", SqlType.String(ShipperCode.value shipper.Code)
                          "shipper_type", SqlType.String kindStr
                          "name", SqlType.String(ShipperName.value shipper.Name)
                          "email", SqlType.String(Email.value shipper.Email)
                          "phone",
                          (match shipper.Phone with
                           | Some p -> SqlType.String(Phone.value p)
                           | None -> SqlType.Null)
                          "contract_number",
                          (match contract with
                           | Some c -> SqlType.String c
                           | None -> SqlType.Null)
                          "discount_rate", SqlType.Decimal rate
                          "now", SqlType.String(now.UtcDateTime.ToString("o")) ]
                    |> Db.exec

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("ShipperRepository", ex.Message))
            }

        { ExistsByEmail = existsByEmail
          Save = save }
