namespace CargoTracker.Billing.Application

open System
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain

// Billing コンテキストのアプリケーション層（US-ADM-01: 割引ポリシー管理 / US21-23: 料金算出・精算）。
// 永続化・外部連携は Port（関数レコード）で抽象化し、ドメインは純粋に保つ。

/// 割引ポリシーマスタのリポジトリポート（US-ADM-01）。
type DiscountPolicyRepository =
    { Save: DiscountPolicyMaster -> Async<Result<int64, DomainError>>
      Update: DiscountPolicyMaster -> Async<Result<unit, DomainError>>
      FindById: int64 -> Async<Result<DiscountPolicyMaster option, DomainError>>
      FindAll: unit -> Async<Result<DiscountPolicyMaster list, DomainError>>
      FindEffective: DateOnly -> Async<Result<DiscountPolicyMaster list, DomainError>> }

/// 精算書リポジトリポート（US21-23）。
type InvoiceRepository =
    { Save: Invoice -> Async<Result<unit, DomainError>>
      Update: Invoice -> Async<Result<unit, DomainError>>
      FindByInvoiceId: InvoiceId -> Async<Result<Invoice option, DomainError>>
      FindByBookingId: BillingBookingId -> Async<Result<Invoice option, DomainError>> }

/// 荷主への通知ポート（US23: 精算書通知 / 期限超過通知）。
type BillingNotifier =
    { Notify: BillingBookingId -> string -> Async<Result<unit, DomainError>> }

/// 決済機関との連携 ACL（US23: 入金確認）。WireMock.Net で契約を固定する。
type PaymentGatewayPort =
    { ConfirmPayment: InvoiceId -> Money -> Async<Result<DateTimeOffset, DomainError>> }

module ManageDiscountPolicy =

    open FsToolkit.ErrorHandling

    /// 割引ポリシーを登録する（US-ADM-01）。割引率は DiscountRate で 0〜30% を保証済み。
    let register
        (repo: DiscountPolicyRepository)
        (policy: DiscountPolicy)
        (rate: DiscountRate)
        (condition: string)
        (effectiveFrom: DateOnly)
        (effectiveTo: DateOnly option)
        : Async<Result<int64, DomainError>> =
        let master =
            DiscountPolicyMaster.create policy rate condition effectiveFrom effectiveTo

        repo.Save master

    /// 割引ポリシーを変更する（US-ADM-01 受入 4）。
    let update (repo: DiscountPolicyRepository) (master: DiscountPolicyMaster) : Async<Result<unit, DomainError>> =
        repo.Update master

    /// 割引ポリシーを無効化する（US-ADM-01 受入 5）。
    let deactivate (repo: DiscountPolicyRepository) (id: int64) : Async<Result<unit, DomainError>> =
        asyncResult {
            let! found = repo.FindById id

            let! master =
                match found with
                | Some m -> Ok m
                | None -> Error(NotFound("DiscountPolicy", string id))

            do! repo.Update(DiscountPolicyMaster.deactivate master)
        }

module Billing =

    open FsToolkit.ErrorHandling

    /// 料金算出→精算書発行（US21/US22/US23）。基本料金に法人割引を適用して精算書を発行し、
    /// 荷主へ通知する。同一予約の重複発行は拒否する。
    let generateInvoice
        (repo: InvoiceRepository)
        (notifier: BillingNotifier)
        (newId: IdGenerator)
        (bookingId: BillingBookingId)
        (shipperId: BillingShipperId)
        (baseAmount: Money)
        (policy: DiscountPolicy)
        (issuedAt: DateTimeOffset)
        : Async<Result<Invoice, DomainError>> =
        asyncResult {
            let! existing = repo.FindByBookingId bookingId

            do!
                match existing with
                | Some _ -> Error(BusinessRuleViolation("AlreadyInvoiced", "この予約はすでに精算書が発行されています。"))
                | None -> Ok()

            let invoiceId = InvoiceId.generate newId
            let! invoice, _events = Invoice.generate invoiceId bookingId shipperId baseAmount policy issuedAt
            do! repo.Save invoice

            let message =
                sprintf
                    "精算書 %s を発行しました。請求金額 %d 円・支払期限は発行から 30 日です。"
                    (InvoiceId.value invoiceId)
                    invoice.FinalAmount.Amount

            do! notifier.Notify bookingId message
            return invoice
        }

    /// 料金算出→精算書発行（割引率を直接適用・IT8・US22）。割引ポリシーマスタ（US-ADM-01）から
    /// 合成層が解決した割引率を適用し、マスタの `discount_rate` を権威とする。同一予約の重複発行は拒否する。
    let generateInvoiceWithRate
        (repo: InvoiceRepository)
        (notifier: BillingNotifier)
        (newId: IdGenerator)
        (bookingId: BillingBookingId)
        (shipperId: BillingShipperId)
        (baseAmount: Money)
        (discountRate: DiscountRate)
        (issuedAt: DateTimeOffset)
        : Async<Result<Invoice, DomainError>> =
        asyncResult {
            let! existing = repo.FindByBookingId bookingId

            do!
                match existing with
                | Some _ -> Error(BusinessRuleViolation("AlreadyInvoiced", "この予約はすでに精算書が発行されています。"))
                | None -> Ok()

            let invoiceId = InvoiceId.generate newId

            let invoice, _events =
                Invoice.generateWithRate invoiceId bookingId shipperId baseAmount discountRate issuedAt

            do! repo.Save invoice

            let message =
                sprintf
                    "精算書 %s を発行しました。請求金額 %d 円・支払期限は発行から 30 日です。"
                    (InvoiceId.value invoiceId)
                    invoice.FinalAmount.Amount

            do! notifier.Notify bookingId message
            return invoice
        }

    /// 入金確認（US23）。決済 ACL で入金を確認し、精算書を Confirmed へ遷移する。
    /// 呼び出し側（合成層）は Confirmed 後に Booking を Settled へ同期する。
    let confirmPayment
        (repo: InvoiceRepository)
        (gateway: PaymentGatewayPort)
        (invoiceId: InvoiceId)
        : Async<Result<Invoice, DomainError>> =
        asyncResult {
            let! found = repo.FindByInvoiceId invoiceId

            let! invoice =
                match found with
                | Some i -> Ok i
                | None -> Error(NotFound("Invoice", InvoiceId.value invoiceId))

            let! paidAt = gateway.ConfirmPayment invoiceId invoice.FinalAmount
            let! updated, _events = Invoice.execute invoice (ConfirmPayment paidAt)
            do! repo.Update updated
            return updated
        }

    /// 返金（US23・IT8 task5.2）。確定済み（Confirmed）の精算書を Refunded へ遷移する。
    /// キャンセル・過誤請求時の払い戻し導線。遷移不可（未確定など）はドメインが拒否する。
    let refund
        (repo: InvoiceRepository)
        (notifier: BillingNotifier)
        (invoiceId: InvoiceId)
        (refundedAt: DateTimeOffset)
        : Async<Result<Invoice, DomainError>> =
        asyncResult {
            let! found = repo.FindByInvoiceId invoiceId

            let! invoice =
                match found with
                | Some i -> Ok i
                | None -> Error(NotFound("Invoice", InvoiceId.value invoiceId))

            let! updated, _events = Invoice.execute invoice (IssueRefund refundedAt)
            do! repo.Update updated

            do! notifier.Notify updated.CargoBookingId (sprintf "精算書 %s の返金を行いました。" (InvoiceId.value invoiceId))

            return updated
        }

    /// 期限超過の検出と未払い通知（US23 受入 5）。期限内なら状態は変わらない。
    let markOverdueIfDue
        (repo: InvoiceRepository)
        (notifier: BillingNotifier)
        (invoiceId: InvoiceId)
        (now: DateTimeOffset)
        : Async<Result<Invoice, DomainError>> =
        asyncResult {
            let! found = repo.FindByInvoiceId invoiceId

            let! invoice =
                match found with
                | Some i -> Ok i
                | None -> Error(NotFound("Invoice", InvoiceId.value invoiceId))

            match Invoice.execute invoice (MarkOverdue now) with
            | Ok(updated, _) ->
                do! repo.Update updated

                do! notifier.Notify updated.CargoBookingId (sprintf "精算書 %s が支払期限を超過しました。" (InvoiceId.value invoiceId))

                return updated
            | Error(InvalidStateTransition _) -> return invoice // 期限内・既遷移は無処理
            | Error e -> return! Error e
        }
