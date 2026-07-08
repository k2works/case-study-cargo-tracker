namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// DB 接続設定（appsettings の "Database" セクション）。開発は SQLite、
/// ステージング・本番は PostgreSQL を設定する（ADR-0003）。
/// </summary>
public sealed class DatabaseOptions
{
    public const string SectionName = "Database";

    public DatabaseProvider Provider { get; set; } = DatabaseProvider.Postgres;

    public string ConnectionString { get; set; } = string.Empty;
}
