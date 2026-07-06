namespace CargoTracker.Shared.Domain

open System

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
