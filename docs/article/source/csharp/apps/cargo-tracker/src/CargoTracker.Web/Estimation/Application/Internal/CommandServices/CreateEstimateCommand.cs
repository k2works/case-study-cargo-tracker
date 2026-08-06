using CargoTracker.Estimation.Domain.Model;

namespace CargoTracker.Estimation.Application.Internal.CommandServices;

/// <summary>輸送見積作成コマンド（US01）。</summary>
public sealed record CreateEstimateCommand(
    string OriginUnLocode,
    string DestinationUnLocode,
    DateOnly ArrivalDeadline,
    CargoType CargoType,
    decimal WeightKg);
