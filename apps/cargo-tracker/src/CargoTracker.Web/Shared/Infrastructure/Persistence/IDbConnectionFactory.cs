using System.Data;
using Microsoft.Data.Sqlite;
using Npgsql;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>設定されたプロバイダに応じた DB 接続を生成するファクトリ（ADR-0001・IDbConnection 注入）。</summary>
public interface IDbConnectionFactory
{
    IDbConnection Create();
}

public sealed class DbConnectionFactory(DatabaseOptions options) : IDbConnectionFactory
{
    public IDbConnection Create() => options.Provider switch
    {
        DatabaseProvider.Sqlite => new SqliteConnection(options.ConnectionString),
        DatabaseProvider.Postgres => new NpgsqlConnection(options.ConnectionString),
        _ => throw new ArgumentOutOfRangeException(nameof(options), options.Provider, "未対応のプロバイダです。"),
    };
}
