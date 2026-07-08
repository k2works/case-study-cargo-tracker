using CargoTracker.Shared.Application.Persistence;
using Dapper;

namespace CargoTracker.Shipper.Application.Internal.QueryServices;

/// <summary>
/// 荷主一覧表示用の DTO（CQRS 読み取り側・画面表示に最適化）。
/// 可変プロパティで Dapper の型変換に委ねる（SQLite の NUMERIC 型アフィニティで
/// 0 が INTEGER として返る問題を吸収するため。ADR-0003 の二方言差異）。
/// </summary>
public sealed class ShipperListItem
{
    public string ShipperCode { get; set; } = string.Empty;
    public string ShipperType { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string? Phone { get; set; }
    public string? Address { get; set; }
    public decimal DiscountRate { get; set; }
}

/// <summary>荷主のクエリサービス（読み取り最適化）。集約を経由せず DTO へ直接射影する。</summary>
public sealed class FindShipperQueryService(IDbConnectionFactory connectionFactory)
{
    public async Task<IReadOnlyList<ShipperListItem>> FindAllAsync(CancellationToken ct = default)
    {
        using var connection = connectionFactory.Create();
        var items = await connection.QueryAsync<ShipperListItem>(new CommandDefinition(
            """
            SELECT shipper_code AS ShipperCode, shipper_type AS ShipperType, name AS Name,
                   email AS Email, phone AS Phone, address AS Address, discount_rate AS DiscountRate
            FROM shipper
            ORDER BY id
            """,
            cancellationToken: ct));
        return items.ToList();
    }
}
