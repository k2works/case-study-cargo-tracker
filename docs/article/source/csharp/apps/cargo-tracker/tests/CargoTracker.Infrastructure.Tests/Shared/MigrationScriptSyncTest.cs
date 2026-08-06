using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;

namespace CargoTracker.Infrastructure.Tests.Shared;

/// <summary>
/// ADR-0003 コンプライアンス: SQLite と PostgreSQL の DDL スクリプトを同一バージョン番号で
/// 並行管理していることを検証する。片方だけスクリプトを追加するドリフトを CI で早期検出する。
/// </summary>
public class MigrationScriptSyncTest
{
    private static string ExtractVersion(string resourceName, string marker)
    {
        var fileName = resourceName[(resourceName.IndexOf(marker, StringComparison.Ordinal) + marker.Length)..];
        return fileName.Split('_')[0];
    }

    [Fact]
    public void SQLiteとPostgreSQLのスクリプトはバージョン番号が一致する()
    {
        // Given: 埋め込みリソースのスクリプト名一覧
        var names = typeof(DatabaseMigrator).Assembly.GetManifestResourceNames();

        var sqliteVersions = names
            .Where(n => n.Contains(".Scripts.sqlite.", StringComparison.Ordinal))
            .Select(n => ExtractVersion(n, ".Scripts.sqlite."))
            .OrderBy(v => v)
            .ToArray();

        var postgresVersions = names
            .Where(n => n.Contains(".Scripts.postgresql.", StringComparison.Ordinal))
            .Select(n => ExtractVersion(n, ".Scripts.postgresql."))
            .OrderBy(v => v)
            .ToArray();

        // Then: 両方言のバージョン集合が一致し、かつ空でない
        sqliteVersions.Should().NotBeEmpty();
        postgresVersions.Should().Equal(sqliteVersions);
    }
}
