using DbUp;
using DbUp.Engine;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>対応する DB プロバイダ。開発は SQLite、ステージング・本番・テストは PostgreSQL（ADR-0003）。</summary>
public enum DatabaseProvider
{
    Sqlite,
    Postgres,
}

/// <summary>
/// DbUp によるバージョン付き forward-only マイグレーション（ADR-0003）。
/// DDL はプロバイダ別ディレクトリ（Scripts/sqlite・Scripts/postgresql）で同一バージョン番号を並行管理し、
/// 実行時にプロバイダに応じたスクリプト群のみを適用する。
/// </summary>
public static class DatabaseMigrator
{
    public static DatabaseUpgradeResult Migrate(DatabaseProvider provider, string connectionString)
    {
        var assembly = typeof(DatabaseMigrator).Assembly;

        var builder = provider switch
        {
            DatabaseProvider.Sqlite => DeployChanges.To
                .SqliteDatabase(connectionString)
                .WithScriptsEmbeddedInAssembly(assembly, name => name.Contains(".Scripts.sqlite.")),
            DatabaseProvider.Postgres => DeployChanges.To
                .PostgresqlDatabase(connectionString)
                .WithScriptsEmbeddedInAssembly(assembly, name => name.Contains(".Scripts.postgresql.")),
            _ => throw new ArgumentOutOfRangeException(nameof(provider), provider, "未対応のプロバイダです。"),
        };

        return builder.LogToNowhere().Build().PerformUpgrade();
    }
}
