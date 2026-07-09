using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using Microsoft.Data.Sqlite;

namespace CargoTracker.Infrastructure.Tests.Shared;

public sealed class DatabaseMigratorTest : IDisposable
{
    private readonly string _dbPath = Path.Combine(Path.GetTempPath(), $"ct_{Guid.NewGuid():N}.db");
    private string ConnectionString => $"Data Source={_dbPath}";

    public void Dispose()
    {
        SqliteConnection.ClearAllPools();
        if (File.Exists(_dbPath))
        {
            File.Delete(_dbPath);
        }
        GC.SuppressFinalize(this);
    }

    private int CountTable(string tableName)
    {
        using var connection = new SqliteConnection(ConnectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=$name";
        command.Parameters.AddWithValue("$name", tableName);
        return Convert.ToInt32(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture);
    }

    private int CountUniqueIndex(string indexName)
    {
        using var connection = new SqliteConnection(ConnectionString);
        connection.Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=$name AND sql LIKE '%UNIQUE%'";
        command.Parameters.AddWithValue("$name", indexName);
        return Convert.ToInt32(command.ExecuteScalar(), System.Globalization.CultureInfo.InvariantCulture);
    }

    [Fact]
    public void SQLiteに初期スキーマを適用しusersテーブルが作成される()
    {
        // When: SQLite プロバイダでマイグレーションを実行する
        var result = DatabaseMigrator.Migrate(DatabaseProvider.Sqlite, ConnectionString);

        // Then: 成功し users テーブルが存在する
        result.Successful.Should().BeTrue();
        CountTable("users").Should().Be(1);
    }

    [Fact]
    public void 二重適用してもべき等でエラーにならない()
    {
        // Given: 一度適用済み
        DatabaseMigrator.Migrate(DatabaseProvider.Sqlite, ConnectionString);

        // When: 再度適用する（journal により再実行されない）
        var second = DatabaseMigrator.Migrate(DatabaseProvider.Sqlite, ConnectionString);

        // Then: 成功する
        second.Successful.Should().BeTrue();
    }

    [Fact]
    public void SQLiteマイグレーションで荷主メールアドレスの一意制約が作成される()
    {
        var result = DatabaseMigrator.Migrate(DatabaseProvider.Sqlite, ConnectionString);

        result.Successful.Should().BeTrue();
        CountUniqueIndex("uk_shipper_email").Should().Be(1);
    }
}
