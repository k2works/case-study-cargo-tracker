using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Application.Internal.CommandServices;

/// <summary>経路候補を選択して確定するコマンド（US09）。Route は Routing BC 固有の CandidateRoute。</summary>
public sealed record SelectRouteCommand(string BookingId, CandidateRoute Route);
