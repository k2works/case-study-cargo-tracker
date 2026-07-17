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
