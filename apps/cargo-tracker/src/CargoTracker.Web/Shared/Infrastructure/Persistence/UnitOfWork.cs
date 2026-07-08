using System.Data;
using CargoTracker.Shared.Domain.Model;
using MediatR;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// <see cref="IUnitOfWork"/> の実装。1 ユースケース = 1 トランザクションを原則とし、
/// コミット成功後にのみ追跡集約のドメインイベントを MediatR で発行する（ADR-0002）。
/// コミットしないまま破棄された場合はロールバックし、イベントは発行しない。
/// </summary>
public sealed class UnitOfWork : IUnitOfWork
{
    private readonly IDbConnection _connection;
    private readonly IDbTransaction _transaction;
    private readonly IPublisher _publisher;
    private readonly List<AggregateRoot> _tracked = [];
    private bool _committed;

    public UnitOfWork(IDbConnection connection, IPublisher publisher)
    {
        _connection = connection;
        _publisher = publisher;
        if (_connection.State != ConnectionState.Open)
        {
            _connection.Open();
        }
        _transaction = _connection.BeginTransaction();
    }

    public IDbTransaction Transaction => _transaction;

    public void Track(AggregateRoot aggregate) => _tracked.Add(aggregate);

    public async Task CommitAsync(CancellationToken ct = default)
    {
        _transaction.Commit();
        _committed = true;

        // コミット成功後にのみイベントを回収して発行する（post-commit）。
        var events = _tracked.SelectMany(aggregate => aggregate.PullDomainEvents()).ToArray();
        foreach (var domainEvent in events)
        {
            await _publisher.Publish(domainEvent, ct);
        }
    }

    public ValueTask DisposeAsync()
    {
        if (!_committed)
        {
            // 未コミットのまま破棄された場合はロールバックする。イベントは回収されないため発行されない。
            _transaction.Rollback();
        }
        _transaction.Dispose();
        return ValueTask.CompletedTask;
    }
}
