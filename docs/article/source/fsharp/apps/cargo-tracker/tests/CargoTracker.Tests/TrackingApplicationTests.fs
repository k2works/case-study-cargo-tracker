module CargoTracker.Tests.TrackingApplicationTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Tracking.Domain
open CargoTracker.Tracking.Application

// US19: 遅延例外 / US20: 破損・紛失例外（アプリケーション層）。
// ドメインは TrackingDomainTests で検証済み。ここでは通知・エスカレーションの結線を検証する。

let private fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "テスト前提の Location 生成に失敗: %s" e

let private dto (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

/// 追跡番号発行済みの状態をストアに用意したインメモリリポジトリスタブ。
let private repoStub () =
    let store = System.Collections.Generic.Dictionary<string, TrackingActivity>()
    let bid = TrackingBookingId.ofString "BKG-0001"
    let activity, _ = TrackingActivity.issue (fixedId (Guid.NewGuid())) bid
    let tn = TrackingNumber.value activity.TrackingNumber
    store[tn] <- activity

    let repo =
        { Save =
            fun a _token ->
                async {
                    store[TrackingNumber.value a.TrackingNumber] <- a
                    return Ok()
                }
          FindByTrackingNumber =
            fun trackingNumber ->
                async {
                    match store.TryGetValue(TrackingNumber.value trackingNumber) with
                    | true, a -> return Ok(Some a)
                    | false, _ -> return Ok None
                }
          Update =
            fun a ->
                async {
                    store[TrackingNumber.value a.TrackingNumber] <- a
                    return Ok()
                } }

    repo, store, activity.TrackingNumber

let private notifierStub (calls: System.Collections.Generic.List<string>) : TrackingNotifier =
    { Notify =
        fun _ message ->
            async {
                calls.Add message
                return Ok()
            } }

let private escalationStub (calls: System.Collections.Generic.List<ExceptionType>) : EscalationNotifier =
    { Escalate =
        fun _ exType ->
            async {
                calls.Add exType
                return Ok()
            } }

[<Fact>]
let ``遅延例外の登録は荷主通知しエスカレーションしない（US19）`` () =
    let repo, store, tn = repoStub ()
    let notifies = System.Collections.Generic.List<string>()
    let escalations = System.Collections.Generic.List<ExceptionType>()

    let result =
        ManageException.register
            repo
            (notifierStub notifies)
            (escalationStub escalations)
            tn
            Delay
            (loc "USLAX")
            (dto (2026, 9, 1))
            "荒天による寄港遅延"
        |> Async.RunSynchronously

    match result with
    | Ok updated -> TrackingActivity.currentStatus updated |> should equal InException
    | Error e -> failwithf "%A" e

    notifies.Count |> should equal 1
    escalations.Count |> should equal 0

[<Fact>]
let ``紛失例外の登録は荷主通知と管理職エスカレーションの両方を行う（US20）`` () =
    let repo, _, tn = repoStub ()
    let notifies = System.Collections.Generic.List<string>()
    let escalations = System.Collections.Generic.List<ExceptionType>()

    let result =
        ManageException.register
            repo
            (notifierStub notifies)
            (escalationStub escalations)
            tn
            Lost
            (loc "USLAX")
            (dto (2026, 9, 1))
            "海上事故により紛失"
        |> Async.RunSynchronously

    match result with
    | Ok _ -> ()
    | Error e -> failwithf "%A" e

    notifies.Count |> should equal 1
    escalations |> List.ofSeq |> should equal [ Lost ]

[<Fact>]
let ``存在しない追跡番号への例外登録は NotFound を返す`` () =
    let repo, _, _ = repoStub ()
    let notifies = System.Collections.Generic.List<string>()
    let escalations = System.Collections.Generic.List<ExceptionType>()

    let result =
        ManageException.register
            repo
            (notifierStub notifies)
            (escalationStub escalations)
            (TrackingNumber.ofString "TRK-NOTEXIST")
            Delay
            (loc "USLAX")
            (dto (2026, 9, 1))
            "遅延"
        |> Async.RunSynchronously

    match result with
    | Error(NotFound(entity, _)) -> entity |> should equal "TrackingActivity"
    | other -> failwithf "NotFound を期待したが: %A" other

[<Fact>]
let ``例外解決は状態を復帰し対応報告を通知する（US19）`` () =
    let repo, _, tn = repoStub ()
    let notifies = System.Collections.Generic.List<string>()
    let escalations = System.Collections.Generic.List<ExceptionType>()

    ManageException.register
        repo
        (notifierStub notifies)
        (escalationStub escalations)
        tn
        Delay
        (loc "USLAX")
        (dto (2026, 9, 1))
        "遅延"
    |> Async.RunSynchronously
    |> ignore

    let result =
        ManageException.resolve repo (notifierStub notifies) tn 0 (dto (2026, 9, 2)) "新到着予定日を提示"
        |> Async.RunSynchronously

    match result with
    | Ok updated -> TrackingActivity.currentStatus updated |> should equal NotReceived
    | Error e -> failwithf "%A" e
