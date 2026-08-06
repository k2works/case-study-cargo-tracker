using CargoTracker.Booking.Application.Internal.OutboundServices;
using CargoTracker.Booking.Application.Internal.Services;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using Dapper;

namespace CargoTracker.Booking.Infrastructure.Services;

/// <summary>shipper テーブルだけを参照する ACL 実装。</summary>
public sealed class ShipperExistenceChecker(IDbConnectionFactory connectionFactory) : IShipperExistenceChecker
{
    public async Task<bool> ExistsAsync(ShipperId id, CancellationToken ct = default)
    {
        var surrogateId = ShipperIdCodec.ToSurrogateId(id);
        using var connection = connectionFactory.Create();
        var count = await connection.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT COUNT(1) FROM shipper WHERE id = @Id",
            new { Id = surrogateId }, cancellationToken: ct));
        return count > 0;
    }
}
