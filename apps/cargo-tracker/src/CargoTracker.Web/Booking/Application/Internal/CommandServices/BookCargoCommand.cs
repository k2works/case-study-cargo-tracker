using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.CommandServices;

/// <summary>貨物予約登録コマンド（US04）。ShipperId は画面入力の shipper.id を文字列で受け取る。</summary>
public sealed record BookCargoCommand(
    string ShipperId,
    string OriginUnLocode,
    string DestinationUnLocode,
    DateOnly ArrivalDeadline,
    CargoType CargoType,
    decimal Weight,
    decimal? DimensionLength = null,
    decimal? DimensionWidth = null,
    decimal? DimensionHeight = null,
    int? Quantity = null,
    string? Description = null,
    string? HazardousClass = null,
    string? UnNumber = null,
    string? ProperShippingName = null,
    decimal? MinTemperature = null,
    decimal? MaxTemperature = null,
    TemperatureUnit? TemperatureUnit = null);
