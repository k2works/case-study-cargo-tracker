using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;
using MediatR;
using Microsoft.Data.Sqlite;
using Moq;

namespace CargoTracker.Infrastructure.Tests.Shared;

public class UnitOfWorkTest
{
    private sealed record SampleEvent(string Name) : INotification;

    private sealed class SampleAggregate : AggregateRoot
    {
        public void Raise(string name) => AddDomainEvent(new SampleEvent(name));
    }

    private static SqliteConnection OpenConnection()
    {
        var connection = new SqliteConnection("Data Source=:memory:");
        connection.Open();
        return connection;
    }

    [Fact]
    public async Task コミット成功後に蓄積イベントが発行される()
    {
        // Given: イベントを持つ集約を追跡する UoW
        var publisher = new Mock<IPublisher>();
        await using var connection = OpenConnection();
        var aggregate = new SampleAggregate();
        aggregate.Raise("committed");

        await using var uow = new UnitOfWork(connection, publisher.Object);
        uow.Track(aggregate);

        // When: コミットする
        await uow.CommitAsync();

        // Then: コミット後にイベントが発行される（MediatR は実行時型 SampleEvent でハンドラ解決する）
        publisher.Verify(p => p.Publish(It.IsAny<INotification>(), It.IsAny<CancellationToken>()), Times.Once);
    }

    [Fact]
    public async Task ロールバック時にはイベントが発行されない()
    {
        // Given: イベントを持つ集約を追跡する UoW（コミットしない）
        var publisher = new Mock<IPublisher>();
        await using var connection = OpenConnection();
        var aggregate = new SampleAggregate();
        aggregate.Raise("rolledback");

        // When: コミットせずに破棄する（Dispose でロールバック）
        await using (var uow = new UnitOfWork(connection, publisher.Object))
        {
            uow.Track(aggregate);
        }

        // Then: イベントは一切発行されない（コミット前観測事故の構造的防止・ADR-0002 コンプライアンス）
        publisher.Verify(p => p.Publish(It.IsAny<INotification>(), It.IsAny<CancellationToken>()), Times.Never);
    }

    [Fact]
    public async Task トランザクションはリポジトリ操作のために公開される()
    {
        // Given: UoW
        var publisher = new Mock<IPublisher>();
        await using var connection = OpenConnection();
        await using var uow = new UnitOfWork(connection, publisher.Object);

        // Then: リポジトリが参加するためのトランザクションが取得できる
        uow.Transaction.Should().NotBeNull();
    }
}
