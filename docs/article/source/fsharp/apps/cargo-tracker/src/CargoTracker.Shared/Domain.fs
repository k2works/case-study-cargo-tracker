namespace CargoTracker.Shared.Domain

open System

/// 全コンテキスト共通のドメインエラー表現（domain-model 準拠）。
/// 例外を投げず Railway Oriented Programming で失敗を値として扱う。
type DomainError =
    | ValidationError of field: string * message: string
    | InvalidStateTransition of current: string * attempted: string
    | BusinessRuleViolation of rule: string * message: string
    | NotFound of entity: string * id: string

/// 現在時刻の注入ポート（ADR-0006）。ドメイン関数は DateTimeOffset.Now を直接呼ばず、
/// このポートから取得した値を引数で受け取ることで純粋性を保つ。
type Clock = unit -> DateTimeOffset

/// GUID 生成の注入ポート（ADR-0006）。採番するドメイン関数はこのポートを引数で受ける。
type IdGenerator = unit -> Guid

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

    /// UN/LOCODE による同一性判定。Location は locode のみを保持するため構造的等価と一致する。
    let sameAs (a: Location) (b: Location) : bool = a = b

/// 貨物の輸送フェーズを表す共有 DU（domain-model 準拠・9 ケース）。
/// Tracking Context 固有の TrackingStatus（イベント履歴からの導出値）とは同一ケースだが
/// 別型として分離し、変換は Tracking のアプリケーション層が担う（BC 分離）。
type TransportStatus =
    | NotReceived
    | Received
    | Loaded
    | OnboardCarrier
    | Unloaded
    | AwaitingClaim
    | Claimed
    | InException
    | Unknown

module TransportStatus =

    /// 永続化用の文字列表現。
    let toString (status: TransportStatus) : string =
        match status with
        | NotReceived -> "NOT_RECEIVED"
        | Received -> "RECEIVED"
        | Loaded -> "LOADED"
        | OnboardCarrier -> "ONBOARD_CARRIER"
        | Unloaded -> "UNLOADED"
        | AwaitingClaim -> "AWAITING_CLAIM"
        | Claimed -> "CLAIMED"
        | InException -> "IN_EXCEPTION"
        | Unknown -> "UNKNOWN"

    /// 永続化文字列から復元する。未知の値は Error（domain-model の網羅 + フォールバック方針）。
    let ofString (value: string) : Result<TransportStatus, DomainError> =
        match value with
        | "NOT_RECEIVED" -> Ok NotReceived
        | "RECEIVED" -> Ok Received
        | "LOADED" -> Ok Loaded
        | "ONBOARD_CARRIER" -> Ok OnboardCarrier
        | "UNLOADED" -> Ok Unloaded
        | "AWAITING_CLAIM" -> Ok AwaitingClaim
        | "CLAIMED" -> Ok Claimed
        | "IN_EXCEPTION" -> Ok InException
        | "UNKNOWN" -> Ok Unknown
        | other -> Error(ValidationError("TransportStatus", sprintf "未知の輸送状態です: %s" other))
