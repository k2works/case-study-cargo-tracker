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

module InvoiceRepository =

    /// 支払い状態を永続値へ写像（PaymentState DU → payment_status + paid_at/due_date）。
    let private paymentColumns (p: PaymentState) : string * string option * string option =
        match p with
        | Pending due -> "PENDING", Some(due.UtcDateTime.ToString("o")), None
        | Confirmed paidAt -> "CONFIRMED", None, Some(paidAt.UtcDateTime.ToString("o"))
        | Overdue due -> "OVERDUE", Some(due.UtcDateTime.ToString("o")), None
        | Refunded refundedAt -> "REFUNDED", None, Some(refundedAt.UtcDateTime.ToString("o"))

    /// 永続値から PaymentState を復元。
    let private toPaymentState
        (status: string)
        (dueDate: string option)
        (paidAt: string option)
        : Result<PaymentState, DomainError> =
        let parse (s: string) =
            DateTimeOffset.Parse(s, null, Globalization.DateTimeStyles.RoundtripKind)

        match status with
        | "PENDING" ->
            match dueDate with
            | Some d -> Ok(Pending(parse d))
            | None -> Error(ValidationError("payment_status", "PENDING には due_date が必要です。"))
        | "OVERDUE" ->
            match dueDate with
            | Some d -> Ok(Overdue(parse d))
            | None -> Error(ValidationError("payment_status", "OVERDUE には due_date が必要です。"))
        | "CONFIRMED" ->
            match paidAt with
            | Some p -> Ok(Confirmed(parse p))
            | None -> Error(ValidationError("payment_status", "CONFIRMED には paid_at が必要です。"))
        | "REFUNDED" ->
            match paidAt with
            | Some p -> Ok(Refunded(parse p))
            | None -> Error(ValidationError("payment_status", "REFUNDED には paid_at が必要です。"))
        | other -> Error(ValidationError("payment_status", sprintf "未知の支払い状態です: %s" other))

    let private reconstruct
        (
            invoiceNumber: string,
            bookingId: string,
            shipperId: string,
            baseValue: int64,
            baseCurrency: string,
            rate: decimal,
            finalValue: int64,
            finalCurrency: string,
            status: string,
            issuedAt: string,
            dueDate: string option,
            paidAt: string option
        ) : Result<Invoice, DomainError> =
        result {
            let! baseCur = CurrencyCode.ofString baseCurrency
            let! finalCur = CurrencyCode.ofString finalCurrency
            let! discountRate = DiscountRate.create rate
            let! bid = BillingBookingId.create bookingId
            // 荷主の法人判定は精算書に埋め込まず、shipper_id のみ復元する（割引率は確定済み）。
            let shipper =
                { ShipperId = shipperId
                  IsCorporate = false }

            let! payment = toPaymentState status dueDate paidAt

            return
                { InvoiceId = InvoiceId.ofString invoiceNumber
                  CargoBookingId = bid
                  ShipperId = shipper
                  BaseAmount =
                    { Amount = baseValue
                      Currency = baseCur }
                  DiscountRate = discountRate
                  FinalAmount =
                    { Amount = finalValue
                      Currency = finalCur }
                  IssuedAt = DateTimeOffset.Parse(issuedAt, null, Globalization.DateTimeStyles.RoundtripKind)
                  Payment = payment }
        }

    let private readRow (rd: IDataReader) =
        rd.ReadString "invoice_number",
        rd.ReadString "booking_id",
        rd.ReadString "shipper_id",
        rd.ReadInt64 "base_amount_value",
        rd.ReadString "base_amount_currency",
        rd.ReadDecimal "discount_rate",
        rd.ReadInt64 "final_amount_value",
        rd.ReadString "final_amount_currency",
        rd.ReadString "payment_status",
        rd.ReadString "issued_at",
        rd.ReadStringOption "due_date",
        rd.ReadStringOption "paid_at"

    let create (conn: IDbConnection) (clock: Clock) : InvoiceRepository =

        let now () = (clock ()).UtcDateTime.ToString("o")

        let save (invoice: Invoice) : Async<Result<unit, DomainError>> =
            async {
                try
                    let status, dueDate, paidAt = paymentColumns invoice.Payment
                    let nowStr = now ()

                    conn
                    |> Db.newCommand
                        """
                        INSERT INTO invoice
                            (invoice_number, booking_id, shipper_id, base_amount_value, base_amount_currency,
                             discount_rate, final_amount_value, final_amount_currency, payment_status,
                             issued_at, due_date, paid_at, created_at, updated_at)
                        VALUES (@num, @bid, @sid, @bval, @bcur, @rate, @fval, @fcur, @status, @issued, @due, @paid, @now, @now)
                        """
                    |> Db.setParams
                        [ "num", SqlType.String(InvoiceId.value invoice.InvoiceId)
                          "bid", SqlType.String(BillingBookingId.value invoice.CargoBookingId)
                          "sid", SqlType.String invoice.ShipperId.ShipperId
                          "bval", SqlType.Int64 invoice.BaseAmount.Amount
                          "bcur", SqlType.String(CurrencyCode.toString invoice.BaseAmount.Currency)
                          "rate", SqlType.Decimal(DiscountRate.value invoice.DiscountRate)
                          "fval", SqlType.Int64 invoice.FinalAmount.Amount
                          "fcur", SqlType.String(CurrencyCode.toString invoice.FinalAmount.Currency)
                          "status", SqlType.String status
                          "issued", SqlType.String(invoice.IssuedAt.UtcDateTime.ToString("o"))
                          "due",
                          (match dueDate with
                           | Some d -> SqlType.String d
                           | None -> SqlType.Null)
                          "paid",
                          (match paidAt with
                           | Some p -> SqlType.String p
                           | None -> SqlType.Null)
                          "now", SqlType.String nowStr ]
                    |> Db.exec

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("InvoiceRepository", ex.Message))
            }

        let update (invoice: Invoice) : Async<Result<unit, DomainError>> =
            async {
                try
                    let status, dueDate, paidAt = paymentColumns invoice.Payment

                    conn
                    |> Db.newCommand
                        """
                        UPDATE invoice
                        SET payment_status = @status, due_date = @due, paid_at = @paid, updated_at = @now
                        WHERE invoice_number = @num
                        """
                    |> Db.setParams
                        [ "status", SqlType.String status
                          "due",
                          (match dueDate with
                           | Some d -> SqlType.String d
                           | None -> SqlType.Null)
                          "paid",
                          (match paidAt with
                           | Some p -> SqlType.String p
                           | None -> SqlType.Null)
                          "now", SqlType.String(now ())
                          "num", SqlType.String(InvoiceId.value invoice.InvoiceId) ]
                    |> Db.exec

                    return Ok()
                with ex ->
                    return Error(BusinessRuleViolation("InvoiceRepository", ex.Message))
            }

        let findBy (whereColumn: string) (paramValue: string) : Async<Result<Invoice option, DomainError>> =
            async {
                try
                    let row =
                        conn
                        |> Db.newCommand (
                            sprintf
                                """
                                SELECT invoice_number, booking_id, shipper_id, base_amount_value, base_amount_currency,
                                       discount_rate, final_amount_value, final_amount_currency, payment_status,
                                       issued_at, due_date, paid_at
                                FROM invoice WHERE %s = @p
                                """
                                whereColumn
                        )
                        |> Db.setParams [ "p", SqlType.String paramValue ]
                        |> Db.querySingle readRow

                    match row with
                    | None -> return Ok None
                    | Some r -> return reconstruct r |> Result.map Some
                with ex ->
                    return Error(BusinessRuleViolation("InvoiceRepository", ex.Message))
            }

        { Save = save
          Update = update
          FindByInvoiceId = fun id -> findBy "invoice_number" (InvoiceId.value id)
          FindByBookingId = fun bid -> findBy "booking_id" (BillingBookingId.value bid) }

/// 精算書の読み取りモデル（US23・一覧表示）。
module InvoiceQueries =

    type InvoiceListRow =
        { InvoiceNumber: string
          BookingId: string
          FinalAmountValue: int64
          PaymentStatus: string }

    let findAll (conn: IDbConnection) : InvoiceListRow list =
        conn
        |> Db.newCommand
            "SELECT invoice_number, booking_id, final_amount_value, payment_status FROM invoice ORDER BY id DESC"
        |> Db.query (fun rd ->
            { InvoiceNumber = rd.ReadString "invoice_number"
              BookingId = rd.ReadString "booking_id"
              FinalAmountValue = rd.ReadInt64 "final_amount_value"
              PaymentStatus = rd.ReadString "payment_status" })
