namespace CargoTracker.Shared.Domain

open System

/// 全コンテキスト共通のドメインエラー表現（domain-model 準拠）。
/// 例外を投げず Railway Oriented Programming で失敗を値として扱う。
type DomainError =
    | ValidationError of field: string * message: string
    | InvalidStateTransition of current: string * attempted: string
    | BusinessRuleViolation of rule: string * message: string
    | NotFound of entity: string * id: string

/// 荷主を一意に識別する共有カーネルの値オブジェクト（Guid ベース）。
type ShipperId = private ShipperId of Guid

module ShipperId =

    /// 既存の Guid から生成する（永続化層からの復元用）。
    let ofGuid (value: Guid) : ShipperId = ShipperId value

    /// 文字列表現から生成する。不正な形式は Error を返す。
    let ofString (value: string) : Result<ShipperId, DomainError> =
        match Guid.TryParse value with
        | true, guid -> Ok(ShipperId guid)
        | false, _ -> Error(ValidationError("ShipperId", "荷主 ID の形式が不正です。"))

    /// 内部の Guid を取り出す。
    let value (ShipperId v) = v

/// UN/LOCODE で識別される地点を表す値オブジェクト。
/// 不正な状態を型で表現不可能にするため、スマートコンストラクタ経由でのみ生成する。
type Location = private Location of string

module Location =

    /// UN/LOCODE は 5 文字の大文字英字。
    let create (code: string) : Result<Location, string> =
        match code with
        | null -> Error "ロケーションコードが null です"
        | c when c.Length <> 5 -> Error "ロケーションコードは 5 文字である必要があります"
        | c when not (c |> Seq.forall (fun ch -> Char.IsLetter ch && Char.IsUpper ch)) ->
            Error "ロケーションコードは大文字英字である必要があります"
        | c -> Ok(Location c)

    /// 内部の文字列表現を取り出す。
    let value (Location code) = code
