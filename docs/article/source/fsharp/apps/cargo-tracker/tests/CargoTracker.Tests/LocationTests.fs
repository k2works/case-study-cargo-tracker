module CargoTracker.Tests.LocationTests

open Xunit
open FsUnit.Xunit
open FsCheck
open CargoTracker.Shared.Domain

[<Fact>]
let ``正常な UN/LOCODE は Ok を返す`` () =
    match Location.create "JPTYO" with
    | Ok loc -> Location.value loc |> should equal "JPTYO"
    | Error e -> failwithf "Ok を期待したが Error: %s" e

[<Fact>]
let ``5 文字でないコードは Error を返す`` () =
    match Location.create "JP" with
    | Error msg -> msg |> should equal "ロケーションコードは 5 文字である必要があります"
    | Ok _ -> failwith "Error を期待したが Ok"

/// FsCheck プロパティ: 5 文字の大文字英字なら常に Ok を返す。
[<Fact>]
let ``5 文字の大文字英字なら常に Ok を返す`` () =
    let property (a: char) (b: char) (c: char) (d: char) (e: char) =
        let toUpperLetter (ch: char) = 'A' + char (int ch % 26)

        let code =
            System.String(
                [| toUpperLetter a
                   toUpperLetter b
                   toUpperLetter c
                   toUpperLetter d
                   toUpperLetter e |]
            )

        match Location.create code with
        | Ok loc -> Location.value loc = code
        | Error _ -> false

    Check.QuickThrowOnFailure property
