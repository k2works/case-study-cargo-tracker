namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// DateTimeOffset を DB 保存用の DateTime（UTC・Kind=Unspecified）へ変換する共通ヘルパ。
/// 二方言（PostgreSQL/SQLite）で一貫した時刻表現を保証する（BC 横断の永続化規約）。
/// </summary>
public static class DatabaseTimestamp
{
    /// <summary>DB 保存用に UTC・Unspecified の DateTime へ正規化する。</summary>
    public static DateTime ToDatabase(DateTimeOffset value)
        => DateTime.SpecifyKind(value.UtcDateTime, DateTimeKind.Unspecified);
}
