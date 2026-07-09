using System.Data;
using CargoTracker.Shipper.Domain;
using CargoTracker.Shipper.Domain.Repositories;
using Dapper;
using Microsoft.Data.Sqlite;
using Npgsql;
using ShipperAggregate = CargoTracker.Shipper.Domain.Model.Shipper;

namespace CargoTracker.Shipper.Infrastructure.Repositories;

/// <summary>
/// Dapper による荷主リポジトリ（ADR-0001）。書き込みは UoW のトランザクション上で実行する。
/// SQL は ADR-0003 の方言禁止規約に従い、タイムスタンプは C# 側で生成する（NOW() 不使用）。
/// </summary>
public sealed class ShipperRepository : IShipperRepository
{
    public async Task<bool> ExistsByEmailAsync(string email, IDbTransaction transaction, CancellationToken ct = default)
    {
        var count = await transaction.Connection!.ExecuteScalarAsync<long>(new CommandDefinition(
            "SELECT COUNT(1) FROM shipper WHERE email = @Email",
            new { Email = email }, transaction, cancellationToken: ct));
        return count > 0;
    }

    public async Task SaveAsync(ShipperAggregate shipper, IDbTransaction transaction, CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow;
        try
        {
            await transaction.Connection!.ExecuteAsync(new CommandDefinition(
                """
                INSERT INTO shipper
                    (shipper_code, shipper_type, name, email, phone, address, contract_number, discount_rate, created_at, updated_at, version)
                VALUES
                    (@Code, @Type, @Name, @Email, @Phone, @Address, @ContractNumber, @DiscountRate, @CreatedAt, @UpdatedAt, 0)
                """,
                new
                {
                    Code = shipper.Code.Value,
                    Type = shipper.Type.ToString().ToUpperInvariant(),
                    Name = shipper.Name.Value,
                    Email = shipper.Email.Value,
                    Phone = shipper.Phone?.Value,
                    Address = shipper.Address?.Value,
                    ContractNumber = shipper.ContractNumber?.Value,
                    DiscountRate = shipper.DiscountRate.Value,
                    CreatedAt = now,
                    UpdatedAt = now,
                },
                transaction,
                cancellationToken: ct));
        }
        catch (Exception ex) when (IsEmailUniqueViolation(ex))
        {
            throw new EmailAlreadyRegisteredException(shipper.Email.Value);
        }
    }

    private static bool IsEmailUniqueViolation(Exception exception) =>
        exception is PostgresException { SqlState: PostgresErrorCodes.UniqueViolation, ConstraintName: "uk_shipper_email" }
        || exception is SqliteException { SqliteErrorCode: 19 } sqliteException
            && sqliteException.Message.Contains("shipper.email", StringComparison.OrdinalIgnoreCase);
}
