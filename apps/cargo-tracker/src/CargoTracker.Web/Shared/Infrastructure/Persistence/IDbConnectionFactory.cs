using System.Data;
using CargoTracker.Shared.Application.Persistence;
using Microsoft.Data.Sqlite;
using Npgsql;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// <see cref="IDbConnectionFactory"/> の具象アダプター。設定されたプロバイダに応じた
/// DB 接続を生成する（ADR-0001・IDbConnection 注入）。
/// </summary>
public sealed class DbConnectionFactory(DatabaseOptions options) : IDbConnectionFactory
{
    public IDbConnection Create() => options.Provider switch
    {
        DatabaseProvider.Sqlite => new SqliteConnection(options.ConnectionString),
        DatabaseProvider.Postgres => new NpgsqlConnection(options.ConnectionString),
        _ => throw new ArgumentOutOfRangeException(nameof(options), options.Provider, "未対応のプロバイダです。"),
    };
}
