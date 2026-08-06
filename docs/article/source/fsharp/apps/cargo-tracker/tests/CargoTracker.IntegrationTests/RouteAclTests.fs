module CargoTracker.IntegrationTests.RouteAclTests

open System
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Web
open CargoTracker.Routing.Domain

// ADR-0010: Routing.RouteCandidate → Booking.CargoItinerary の ACL 変換を検証する。

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "Location.create 失敗: %A" e

let private routeLeg voyage from' to' (dep: DateTimeOffset) (arr: DateTimeOffset) : RouteLeg =
    { VoyageNumber = VoyageNumber.ofString voyage
      From = loc from'
      To = loc to'
      Departure = dep
      Arrival = arr }

let private dt y m d =
    DateTimeOffset(DateTime(y, m, d, 0, 0, 0, DateTimeKind.Utc))

let private firstLoad itinerary =
    CargoTracker.Booking.Domain.CargoItinerary.firstLoadLocation itinerary
    |> Location.value

let private lastUnload itinerary =
    CargoTracker.Booking.Domain.CargoItinerary.lastUnloadLocation itinerary
    |> Location.value

let private legCount itinerary =
    CargoTracker.Booking.Domain.CargoItinerary.legs itinerary |> List.length

[<Fact>]
let ``直行の経路候補は単一区間の旅程へ変換される`` () =
    let candidate: RouteCandidate =
        { Legs = [ routeLeg "V001" "JPTYO" "USLAX" (dt 2026 8 1) (dt 2026 8 14) ]
          TransitPorts = []
          TransitDays = 13
          EstimatedCost = 156_000m
          IsDirect = true }

    match RouteAcl.toCargoItinerary candidate with
    | Ok itinerary ->
        legCount itinerary |> should equal 1
        firstLoad itinerary |> should equal "JPTYO"
        lastUnload itinerary |> should equal "USLAX"
    | Error e -> failwithf "変換に失敗: %A" e

[<Fact>]
let ``乗継ありの経路候補は連結された複数区間の旅程へ変換される`` () =
    let candidate: RouteCandidate =
        { Legs =
            [ routeLeg "V001" "JPTYO" "SGSIN" (dt 2026 8 1) (dt 2026 8 7)
              routeLeg "V002" "SGSIN" "USLAX" (dt 2026 8 9) (dt 2026 8 20) ]
          TransitPorts = [ loc "SGSIN" ]
          TransitDays = 19
          EstimatedCost = 228_000m
          IsDirect = false }

    match RouteAcl.toCargoItinerary candidate with
    | Ok itinerary ->
        legCount itinerary |> should equal 2
        firstLoad itinerary |> should equal "JPTYO"
        lastUnload itinerary |> should equal "USLAX"
    | Error e -> failwithf "変換に失敗: %A" e

[<Fact>]
let ``連結不整合な経路候補は変換に失敗する`` () =
    let candidate: RouteCandidate =
        { Legs =
            [ routeLeg "V001" "JPTYO" "SGSIN" (dt 2026 8 1) (dt 2026 8 7)
              routeLeg "V002" "CNSHA" "USLAX" (dt 2026 8 9) (dt 2026 8 20) ]
          TransitPorts = [ loc "SGSIN" ]
          TransitDays = 19
          EstimatedCost = 228_000m
          IsDirect = false }

    match RouteAcl.toCargoItinerary candidate with
    | Ok _ -> failwith "連結不整合なのに変換が成功した"
    | Error _ -> ()
