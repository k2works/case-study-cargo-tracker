/// Routing コンテキストと Booking コンテキストの腐敗防止層（ACL・ADR-0010）。
///
/// Routing が算出した経路候補（`Routing.Domain.RouteCandidate`）を、Booking の旅程
/// （`Booking.Domain.CargoItinerary`）へ変換する合成層の純粋関数を提供する。両コンテキストは
/// それぞれ固有の `VoyageNumber` 型を持つため、値へ一度ばらしてから Booking 型へ組み替えることで
/// BC 間の型依存を持ち込まない（Location は共有カーネルのためそのまま引き渡す）。
module CargoTracker.Web.RouteAcl

open CargoTracker.Shared.Domain
open FsToolkit.ErrorHandling

/// Routing の経路区間（`RouteLeg`）を Booking の `Leg` へ変換する。
/// VoyageNumber は Routing 固有型を値へ落として Booking 固有型へ組み替える（BC 分離）。
let private toBookingLeg
    (leg: CargoTracker.Routing.Domain.RouteLeg)
    : Result<CargoTracker.Booking.Domain.Leg, DomainError> =
    result {
        let! voyage =
            CargoTracker.Routing.Domain.VoyageNumber.value leg.VoyageNumber
            |> CargoTracker.Booking.Domain.VoyageNumber.create

        return! CargoTracker.Booking.Domain.Leg.create leg.From leg.To leg.Departure leg.Arrival voyage
    }

/// Routing の経路候補（`RouteCandidate`）を Booking の旅程（`CargoItinerary`）へ変換する。
/// 各区間を Booking `Leg` へ変換し、`CargoItinerary.create` で非空・連結制約を保証する。
let toCargoItinerary
    (candidate: CargoTracker.Routing.Domain.RouteCandidate)
    : Result<CargoTracker.Booking.Domain.CargoItinerary, DomainError> =
    result {
        let! legs = candidate.Legs |> List.traverseResultM toBookingLeg
        return! CargoTracker.Booking.Domain.CargoItinerary.create legs
    }
