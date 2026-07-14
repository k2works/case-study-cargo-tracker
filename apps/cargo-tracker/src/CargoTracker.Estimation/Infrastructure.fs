namespace CargoTracker.Estimation.Infrastructure

open System.Data
open Donald
open CargoTracker.Shared.Domain
open CargoTracker.Estimation.Domain
open CargoTracker.Estimation.Application

// Estimation コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// estimate（親）と route_candidate（子）の 1 対多を保存する。

module EstimateRepository =

    let private cargoTypeToString =
        function
        | General -> "GENERAL"
        | Hazardous -> "HAZARDOUS"
        | Refrigerated -> "REFRIGERATED"

    let private statusToString =
        function
        | Created -> "CREATED"
        | Expired -> "EXPIRED"

    /// Donald の出力ポート実装を生成する。RETURNING を使わず業務キーで surrogate id を再取得する（ADR-0003）。
    let create (conn: IDbConnection) (clock: Clock) : EstimateRepository =

        let save (estimate: Estimate) : Async<Result<unit, DomainError>> =
            async {
                try
                    let now = clock ()
                    let estimateGuid = (EstimateId.value estimate.EstimateId).ToString("D")

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO estimate
                            (estimate_id, origin_unlocode, destination_unlocode, arrival_deadline,
                             cargo_type, weight_kg, status, created_at, updated_at)
                        VALUES
                            (@estimate_id, @origin, @destination, @arrival_deadline,
                             @cargo_type, @weight_kg, @status, @now, @now)
                        """
                    |> Db.setParams
                        [ "estimate_id", SqlType.String estimateGuid
                          "origin", SqlType.String(Location.value estimate.Origin)
                          "destination", SqlType.String(Location.value estimate.Destination)
                          "arrival_deadline", SqlType.String(estimate.ArrivalDeadline.ToString("yyyy-MM-dd"))
                          "cargo_type", SqlType.String(cargoTypeToString estimate.CargoType)
                          "weight_kg", SqlType.Decimal(WeightKg.value estimate.WeightKg)
                          "status", SqlType.String(statusToString estimate.Status)
                          "now", SqlType.String(now.UtcDateTime.ToString("o")) ]
                    |> Db.exec

                    // surrogate id を業務キーで再取得（RETURNING 非依存）
                    let estimateId =
                        conn
                        |> Db.newCommand "SELECT id AS eid FROM estimate WHERE estimate_id = @estimate_id"
                        |> Db.setParams [ "estimate_id", SqlType.String estimateGuid ]
                        |> Db.querySingle (fun rd -> rd.ReadInt64 "eid")
                        |> Option.defaultValue 0L

                    estimate.Candidates
                    |> List.iteri (fun i c ->
                        conn
                        |> Db.newCommand
                            """
                            INSERT INTO route_candidate
                                (estimate_id, voyage_number, transit_port, transit_days, estimated_cost, rank)
                            VALUES
                                (@estimate_id, @voyage_number, @transit_port, @transit_days, @estimated_cost, @rank)
                            """
                        |> Db.setParams
                            [ "estimate_id", SqlType.Int64 estimateId
                              "voyage_number", SqlType.String c.VoyageNumber
                              "transit_port", SqlType.String c.TransitPort
                              "transit_days", SqlType.Int32 c.TransitDays
                              "estimated_cost", SqlType.Decimal c.EstimatedCost
                              "rank", SqlType.Int32(i + 1) ]
                        |> Db.exec)

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("EstimateRepository", ex.Message))
            }

        { Save = save }
