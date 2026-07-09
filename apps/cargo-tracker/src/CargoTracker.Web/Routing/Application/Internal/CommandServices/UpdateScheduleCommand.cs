using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Application.Internal.CommandServices;

public sealed record UpdateScheduleCommand(
    string VoyageNumber,
    long Version,
    string VesselName,
    string Carrier,
    IReadOnlyList<SupportedCargoType> SupportedCargoTypes,
    IReadOnlyList<RegisterCarrierMovementCommand> CarrierMovements);
