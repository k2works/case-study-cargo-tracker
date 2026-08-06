namespace CargoTracker.Shipper.Application

open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain

// Shipper コンテキストのアプリケーション層（US02/US03: 荷主登録ワークフロー）。
// 永続化・ID 採番は Port（関数レコード / 注入ポート）で抽象化し、ドメインは純粋に保つ。

/// 荷主リポジトリの出力ポート（関数レコード）。テストは関数リテラルで差し替える。
type ShipperRepository =
    { ExistsByEmail: Email -> Async<Result<bool, DomainError>>
      Save: Shipper -> Async<Result<unit, DomainError>> }

/// 法人契約情報の入力（UI からの DTO）。
type CorporateInput =
    { ContractNumber: string
      DiscountRate: decimal }

/// 荷主登録コマンド（UI からの DTO）。種別に応じて法人情報を持つ。
type RegisterShipperCommand =
    { Name: string
      Email: string
      Phone: string option
      Address: string option
      Corporate: CorporateInput option }

module ShipperRegistration =

    open FsToolkit.ErrorHandling

    /// 任意項目（None は未入力としてスルー、Some は検証する）を Result<'T option> に変換する。
    let private validateOptional (create: string -> Result<'a, DomainError>) (value: string option) =
        match value with
        | None -> Ok None
        | Some v -> create v |> Result.map Some

    /// 種別入力を検証済み ShipperKind に変換する。
    let private validateKind (corporate: CorporateInput option) : Validation<ShipperKind, DomainError> =
        match corporate with
        | None -> Ok Individual
        | Some c ->
            validation {
                let! contract = ContractNumber.create c.ContractNumber
                and! rate = DiscountRate.create c.DiscountRate
                return Corporate(contract, rate)
            }

    /// 荷主を登録する。入力検証（全エラー収集）→ メール重複チェック → 永続化 → イベント返却。
    let register
        (repo: ShipperRepository)
        (newId: IdGenerator)
        (cmd: RegisterShipperCommand)
        : Async<Result<ShipperRegistered, DomainError>> =
        asyncResult {
            // 入力の適用的検証（フィールドエラーを全収集）
            let! name, email, phone, address, kind =
                validation {
                    let! name = ShipperName.create cmd.Name
                    and! email = Email.create cmd.Email
                    and! phone = validateOptional Phone.create cmd.Phone
                    and! address = validateOptional Address.create cmd.Address
                    and! kind = validateKind cmd.Corporate
                    return name, email, phone, address, kind
                }
                |> Result.mapError List.head

            // メールアドレスの一意制約（ドメイン不変条件の補完）
            let! exists = repo.ExistsByEmail email

            do!
                if exists then
                    Error(BusinessRuleViolation("EmailAlreadyRegistered", "このメールアドレスは既に登録されています。"))
                else
                    Ok()

            let id = ShipperId.ofGuid (newId ())
            let shipper, event = Shipper.register id name email phone address kind
            do! repo.Save shipper
            return event
        }
