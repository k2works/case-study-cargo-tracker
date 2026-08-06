module CargoTracker.ArchTests.MigrationComplianceTests

open System.IO
open Xunit
open FsUnit.Xunit

// ADR-0003 コンプライアンス:
//  1. マイグレーションスクリプトは両方言（sqlite / postgresql）で同名・同数であること
//  2. リポジトリの手書き SQL（Infrastructure.fs）は PostgreSQL 固有の方言を使わないこと

/// テスト実行ディレクトリから遡って CargoTracker.sln を含むリポジトリルートを見つける。
let private repoRoot =
    let rec findUp (dir: DirectoryInfo) =
        if isNull dir then
            failwith "CargoTracker.sln が見つかりません"
        elif File.Exists(Path.Combine(dir.FullName, "CargoTracker.sln")) then
            dir.FullName
        else
            findUp dir.Parent

    findUp (DirectoryInfo(System.AppContext.BaseDirectory))

let private scriptsDir dialect =
    Path.Combine(repoRoot, "db", "scripts", dialect)

[<Fact>]
let ``マイグレーションスクリプトは両方言で同名・同数`` () =
    let names dialect =
        Directory.GetFiles(scriptsDir dialect, "*.sql")
        |> Array.map Path.GetFileName
        |> Array.sort

    let sqlite = names "sqlite"
    let postgresql = names "postgresql"

    sqlite |> should not' (be Empty)
    sqlite |> should equal postgresql

/// PostgreSQL 固有で SQLite に無い方言パターン（リポジトリ SQL では使用禁止）。
/// F# のリストコンス `::` と衝突するキャスト演算子は源泉走査から除外する。
let private forbiddenPatterns =
    [ "NOW()"; "RETURNING"; "ILIKE"; "ON CONFLICT"; "JSONB" ]

[<Fact>]
let ``リポジトリの手書き SQL は PostgreSQL 固有方言を使わない`` () =
    let infraFiles =
        Directory.GetFiles(Path.Combine(repoRoot, "src"), "Infrastructure.fs", SearchOption.AllDirectories)

    infraFiles |> should not' (be Empty)

    let violations =
        infraFiles
        |> Array.collect (fun file ->
            File.ReadAllLines file
            |> Array.indexed
            // F# コメント行（// ...）は除外し、SQL を含む行のみ走査する
            |> Array.filter (fun (_, line) -> not (line.TrimStart().StartsWith("//")))
            |> Array.collect (fun (i, line) ->
                forbiddenPatterns
                |> List.filter (fun p -> line.Contains p)
                |> List.map (fun p -> sprintf "%s:%d %s" (Path.GetFileName file) (i + 1) p)
                |> List.toArray))

    violations |> should be Empty
