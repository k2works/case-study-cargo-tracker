namespace CargoTracker.Billing.Infrastructure

open System
open System.Data
open Donald
open FsToolkit.ErrorHandling
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain
open CargoTracker.Billing.Application

// Billing コンテキストのインフラ層（Donald による手書き SQL リポジトリ・ADR-0004）。

module DiscountPolicyRepository =

    /// 生の割引ポリシー行を DiscountPolicyMaster へ復元する。
    let private reconstruct
        (
            id: int64,
            policyType: string,
            rate: decimal,
            condition: string,
            effFrom: string,
            effTo: string option,
            active: bool
        ) : Result<DiscountPolicyMaster, DomainError> =
        result {
            let! policy = DiscountPolicy.ofString policyType
            let! discountRate = DiscountRate.create rate

            return
                { Id = Some id
                  Policy = policy
                  Rate = discountRate
                  ApplicableCondition = condition
                  EffectiveFrom = DateOnly.Parse effFrom
                  EffectiveTo = effTo |> Option.map DateOnly.Parse
                  Active = active }
        }

    let private readRow (rd: IDataReader) =
        rd.ReadInt64 "id",
        rd.ReadString "policy_type",
        rd.ReadDecimal "discount_rate",
        rd.ReadStringOption "applicable_condition" |> Option.defaultValue "",
        rd.ReadString "effective_from",
        rd.ReadStringOption "effective_to",
        rd.ReadBoolean "active"

    let create (conn: IDbConnection) (clock: Clock) : DiscountPolicyRepository =

        let now () = (clock ()).UtcDateTime.ToString("o")

        let save (master: DiscountPolicyMaster) : Async<Result<int64, DomainError>> =
            async {
                try
                    let nowStr = now ()

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO discount_policy
                            (policy_type, discount_rate, applicable_condition, effective_from, effective_to, active, created_at, updated_at)
                        VALUES (@policy_type, @rate, @condition, @eff_from, @eff_to, @active, @now, @now)
                        """
                    |> Db.setParams
                        [ "policy_type", SqlType.String(DiscountPolicy.toString master.Policy)
                          "rate", SqlType.Decimal(DiscountRate.value master.Rate)
                          "condition", SqlType.String master.ApplicableCondition
                          "eff_from", SqlType.String(master.EffectiveFrom.ToString("o"))
                          "eff_to",
                          (match master.EffectiveTo with
                           | Some d -> SqlType.String(d.ToString("o"))
                           | None -> SqlType.Null)
                          "active", SqlType.Boolean master.Active
                          "now", SqlType.String nowStr ]
                    |> Db.exec

                    let id =
                        conn
                        |> Db.newCommand "SELECT id FROM discount_policy ORDER BY id DESC LIMIT 1"
                        |> Db.querySingle (fun rd -> rd.ReadInt64 "id")
                        |> Option.defaultValue 0L

                    return Ok id
                with ex ->
                    return Error(BusinessRuleViolation("DiscountPolicyRepository", ex.Message))
            }

        let update (master: DiscountPolicyMaster) : Async<Result<unit, DomainError>> =
            async {
                try
                    match master.Id with
                    | None -> return Error(ValidationError("DiscountPolicyMaster", "更新には ID が必要です。"))
                    | Some id ->
                        conn
                        |> Db.newCommand
                            """
                            UPDATE discount_policy
                            SET policy_type = @policy_type, discount_rate = @rate, applicable_condition = @condition,
                                effective_from = @eff_from, effective_to = @eff_to, active = @active, updated_at = @now
                            WHERE id = @id
                            """
                        |> Db.setParams
                            [ "policy_type", SqlType.String(DiscountPolicy.toString master.Policy)
                              "rate", SqlType.Decimal(DiscountRate.value master.Rate)
                              "condition", SqlType.String master.ApplicableCondition
                              "eff_from", SqlType.String(master.EffectiveFrom.ToString("o"))
                              "eff_to",
                              (match master.EffectiveTo with
                               | Some d -> SqlType.String(d.ToString("o"))
                               | None -> SqlType.Null)
                              "active", SqlType.Boolean master.Active
                              "now", SqlType.String(now ())
                              "id", SqlType.Int64 id ]
                        |> Db.exec

                        return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("DiscountPolicyRepository", ex.Message))
            }

        let findById (id: int64) : Async<Result<DiscountPolicyMaster option, DomainError>> =
            async {
                try
                    let row =
                        conn
                        |> Db.newCommand
                            "SELECT id, policy_type, discount_rate, applicable_condition, effective_from, effective_to, active FROM discount_policy WHERE id = @id"
                        |> Db.setParams [ "id", SqlType.Int64 id ]
                        |> Db.querySingle readRow

                    match row with
                    | None -> return Ok None
                    | Some r -> return reconstruct r |> Result.map Some
                with ex ->
                    return Error(BusinessRuleViolation("DiscountPolicyRepository", ex.Message))
            }

        let findAll () : Async<Result<DiscountPolicyMaster list, DomainError>> =
            async {
                try
                    let rows =
                        conn
                        |> Db.newCommand
                            "SELECT id, policy_type, discount_rate, applicable_condition, effective_from, effective_to, active FROM discount_policy ORDER BY id"
                        |> Db.query readRow

                    return rows |> List.traverseResultM reconstruct
                with ex ->
                    return Error(BusinessRuleViolation("DiscountPolicyRepository", ex.Message))
            }

        let findEffective (date: DateOnly) : Async<Result<DiscountPolicyMaster list, DomainError>> =
            async {
                try
                    let rows =
                        conn
                        |> Db.newCommand
                            "SELECT id, policy_type, discount_rate, applicable_condition, effective_from, effective_to, active FROM discount_policy WHERE active = @active ORDER BY id"
                        |> Db.setParams [ "active", SqlType.Boolean true ]
                        |> Db.query readRow

                    return
                        rows
                        |> List.traverseResultM reconstruct
                        |> Result.map (List.filter (DiscountPolicyMaster.isEffectiveOn date))
                with ex ->
                    return Error(BusinessRuleViolation("DiscountPolicyRepository", ex.Message))
            }

        { Save = save
          Update = update
          FindById = findById
          FindAll = findAll
          FindEffective = findEffective }
