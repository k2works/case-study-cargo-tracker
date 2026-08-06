namespace CargoTracker.Shipper.Domain

open System
open CargoTracker.Shared.Domain

// Shipper コンテキストのドメイン層（US02: 荷主登録 / US03: 法人荷主登録）。
// 不正状態を型で表現不能にする（Make Illegal States Unrepresentable）ことが設計の中心。

/// 荷主の業務識別コード（SHP- プレフィックス + Guid 先頭 8 文字）。
type ShipperCode = private ShipperCode of string

module ShipperCode =

    /// ShipperId から決定的にコードを生成する。
    let generate (id: ShipperId) : ShipperCode =
        let head = (ShipperId.value id).ToString("N").Substring(0, 8).ToUpperInvariant()
        ShipperCode(sprintf "SHP-%s" head)

    /// 既存の文字列から復元する（永続化層用）。
    let ofString (value: string) : ShipperCode = ShipperCode value

    let value (ShipperCode v) = v

/// 荷主の氏名または社名（1〜200 文字）。
type ShipperName = private ShipperName of string

module ShipperName =

    let create (value: string) : Result<ShipperName, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("ShipperName", "荷主名は空にできません。"))
        elif value.Length > 200 then
            Error(ValidationError("ShipperName", "荷主名は 200 文字以内でなければなりません。"))
        else
            Ok(ShipperName value)

    let value (ShipperName v) = v

/// メールアドレス。システム全体で一意（重複検出はアプリケーション層）。
type Email = private Email of string

module Email =

    let create (value: string) : Result<Email, DomainError> =
        if
            not (String.IsNullOrWhiteSpace value)
            && System.Text.RegularExpressions.Regex.IsMatch(value, @"^[^@\s]+@[^@\s]+\.[^@\s]+$")
        then
            Ok(Email value)
        else
            Error(ValidationError("Email", "メールアドレスの形式が不正です。"))

    let value (Email v) = v

/// 電話番号（任意、最大 50 文字）。
type Phone = private Phone of string

module Phone =

    let create (value: string) : Result<Phone, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("Phone", "電話番号は空にできません。"))
        elif value.Length > 50 then
            Error(ValidationError("Phone", "電話番号は 50 文字以内でなければなりません。"))
        else
            Ok(Phone value)

    let value (Phone v) = v

/// 住所（任意、最大 500 文字）。
type Address = private Address of string

module Address =

    let create (value: string) : Result<Address, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("Address", "住所は空にできません。"))
        elif value.Length > 500 then
            Error(ValidationError("Address", "住所は 500 文字以内でなければなりません。"))
        else
            Ok(Address value)

    let value (Address v) = v

/// 法人契約番号。
type ContractNumber = private ContractNumber of string

module ContractNumber =

    let create (value: string) : Result<ContractNumber, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("ContractNumber", "契約番号は空にできません。"))
        else
            Ok(ContractNumber value)

    let value (ContractNumber v) = v

/// 割引率（0〜30%）。不変条件をスマートコンストラクタで保証する。
type DiscountRate = private DiscountRate of decimal

module DiscountRate =

    let create (value: decimal) : Result<DiscountRate, DomainError> =
        if value < 0.0000m || value > 0.3000m then
            Error(ValidationError("DiscountRate", "割引率は 0〜30% の範囲でなければなりません。"))
        else
            Ok(DiscountRate value)

    let value (DiscountRate v) = v

/// 荷主種別。継承ではなく DU で表現し、法人は契約番号と割引率を「必ず」持つ。
type ShipperKind =
    | Individual
    | Corporate of ContractNumber * DiscountRate

/// 荷主登録イベント（US02/US03）。
type ShipperRegistered =
    { ShipperId: ShipperId
      ShipperCode: ShipperCode }

/// 集約ルート。荷主情報を管理し、個人・法人の 2 種別を持つ。
type Shipper =
    { Id: ShipperId
      Code: ShipperCode
      Name: ShipperName
      Email: Email
      Phone: Phone option
      Address: Address option
      Kind: ShipperKind }

module Shipper =

    /// 荷主を新規登録する。ShipperCode は ShipperId から自動生成する。
    /// 検証済みの値オブジェクトを受け取り、集約と発行イベントを返す。
    let register
        (id: ShipperId)
        (name: ShipperName)
        (email: Email)
        (phone: Phone option)
        (address: Address option)
        (kind: ShipperKind)
        : Shipper * ShipperRegistered =
        let code = ShipperCode.generate id

        let shipper =
            { Id = id
              Code = code
              Name = name
              Email = email
              Phone = phone
              Address = address
              Kind = kind }

        shipper, { ShipperId = id; ShipperCode = code }

    /// 適用される割引率を取得する。個人は 0%。
    let effectiveDiscountRate (shipper: Shipper) : decimal =
        match shipper.Kind with
        | Individual -> 0.0000m
        | Corporate(_, rate) -> DiscountRate.value rate
