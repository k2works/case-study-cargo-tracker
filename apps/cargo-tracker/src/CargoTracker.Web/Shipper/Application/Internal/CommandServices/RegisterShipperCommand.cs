namespace CargoTracker.Shipper.Application.Internal.CommandServices;

/// <summary>荷主登録コマンド（US02 個人 / US03 法人）。法人のみ契約番号・割引率を持つ。</summary>
public sealed record RegisterShipperCommand(
    bool IsCorporate,
    string Name,
    string Email,
    string? Phone,
    string? Address,
    string? ContractNumber,
    decimal? DiscountRate);
