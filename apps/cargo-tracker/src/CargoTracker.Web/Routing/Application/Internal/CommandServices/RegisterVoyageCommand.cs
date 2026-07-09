using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Application.Internal.CommandServices;

public sealed record RegisterVoyageCommand(
    string VoyageNumber,
    string VesselName,
    string Carrier,
    IReadOnlyList<SupportedCargoType> SupportedCargoTypes,
    IReadOnlyList<RegisterCarrierMovementCommand> CarrierMovements);

public sealed record RegisterCarrierMovementCommand(
    string DepartureLocationUnLocode,
    string ArrivalLocationUnLocode,
    DateTimeOffset DepartureDate,
    DateTimeOffset ArrivalDate,
    int SequenceNumber);
