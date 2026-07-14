namespace CargoTracker.Estimation.Infrastructure

open System.Data
open Donald
open CargoTracker.Shared.Domain
open CargoTracker.Estimation.Domain
open CargoTracker.Estimation.Application

// Estimation コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。
// estimate（親）と route_candidate（子）の 1 対多を保存する。

/// 見積一覧の読み取りモデル（CQRS Read 側）。
type EstimateListItem =
    { EstimateId: string
      Origin: string
      Destination: string
      ArrivalDeadline: string
      CargoType: string
      WeightKg: decimal
      Status: string
      CandidateCount: int }

module EstimateQueries =

    let findAll (conn: IDbConnection) : EstimateListItem list =
        conn
        |> Db.newCommand
            """
            SELECT e.estimate_id, e.origin_unlocode, e.destination_unlocode, e.arrival_deadline,
                   e.cargo_type, e.weight_kg, e.status,
                   (SELECT COUNT(*) FROM route_candidate rc WHERE rc.estimate_id = e.id) AS candidate_count
            FROM estimate e
            ORDER BY e.created_at DESC
            """
        |> Db.query (fun rd ->
            { EstimateId = rd.ReadString "estimate_id"
              Origin = rd.ReadString "origin_unlocode"
              Destination = rd.ReadString "destination_unlocode"
              ArrivalDeadline = rd.ReadString "arrival_deadline"
              CargoType = rd.ReadString "cargo_type"
              WeightKg = rd.ReadDecimal "weight_kg"
              Status = rd.ReadString "status"
              CandidateCount = rd.ReadInt32 "candidate_count" })

/// 外部経路システムの ACL スタブ（IT1）。重量ベースの固定コストでルート候補を返す。
/// IT3 で WireMock.Net による契約テスト付きの実サービスへ差し替える。
module StubRoutingService =

    let create () : ExternalRoutingServicePort =
        { FetchCandidateRoutes =
            fun (_query: RouteQuery) ->
                async {
                    let baseCost = 100_000m

                    let candidates =
                        [ RouteCandidate.create "V001" "SGSIN" 21 baseCost
                          RouteCandidate.create "V002" "HKHKG" 25 (baseCost * 0.9m) ]
                        |> List.choose (function
                            | Ok c -> Some c
                            | Error _ -> None)

                    return Ok candidates
                } }

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
