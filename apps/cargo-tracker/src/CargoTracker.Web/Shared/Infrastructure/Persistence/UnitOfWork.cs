using System.Data;
using CargoTracker.Shared.Application.Persistence;
using CargoTracker.Shared.Domain.Model;
using MediatR;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// <see cref="IUnitOfWork"/> の実装。1 ユースケース = 1 トランザクションを原則とし、
/// コミット成功後にのみ追跡集約のドメインイベントを MediatR で発行する（ADR-0002）。
/// コミットしないまま破棄された場合はロールバックし、イベントは発行しない。
/// 生成時に受け取った接続の生存も本作業単位が所有し、破棄時に解放する。
/// </summary>
public sealed class UnitOfWork : IUnitOfWork
{
    private readonly IDbConnection _connection;
    private readonly IDbTransaction _transaction;
    private readonly AmbientTransaction _ambient;
    private readonly IPublisher _publisher;
    private readonly List<AggregateRoot> _tracked = [];
    private bool _committed;
    private bool _released;

    public UnitOfWork(IDbConnection connection, IPublisher publisher, AmbientTransaction ambient)
    {
        _connection = connection;
        _publisher = publisher;
        _ambient = ambient;
        if (_connection.State != ConnectionState.Open)
        {
            _connection.Open();
        }
        _transaction = _connection.BeginTransaction();
        _ambient.Begin(_transaction);
    }

    public void Track(AggregateRoot aggregate) => _tracked.Add(aggregate);

    public async Task CommitAsync(CancellationToken ct = default)
    {
        _transaction.Commit();
        _committed = true;

        // コミット成功後にのみイベントを回収する（post-commit）。
        var events = _tracked.SelectMany(aggregate => aggregate.PullDomainEvents()).ToArray();

        // イベント発行前にトランザクションとアンビエントを解放する。
        // これにより post-commit ハンドラが自身の UoW（新規トランザクション）を開始でき、
        // AmbientTransaction のネスト前提違反（ADR-0006）を避けられる。
        ReleaseTransaction();

        foreach (var domainEvent in events)
        {
            await _publisher.Publish(domainEvent, ct);
        }
    }

    public ValueTask DisposeAsync()
    {
        if (!_committed && !_released)
        {
            // 未コミットのまま破棄された場合はロールバックする。イベントは回収されないため発行されない。
            _transaction.Rollback();
        }
        ReleaseTransaction();
        _connection.Dispose();
        return ValueTask.CompletedTask;
    }

    /// <summary>トランザクションを破棄しアンビエントを解除する（多重呼び出しは無害）。</summary>
    private void ReleaseTransaction()
    {
        if (_released)
        {
            return;
        }
        _released = true;
        _transaction.Dispose();
        _ambient.Clear();
    }
}

/// <summary><see cref="IUnitOfWorkFactory"/> の実装。接続を生成し作業単位を開始する。</summary>
public sealed class UnitOfWorkFactory(
    IDbConnectionFactory connectionFactory,
    IPublisher publisher,
    AmbientTransaction ambient) : IUnitOfWorkFactory
{
    public IUnitOfWork Begin() => new UnitOfWork(connectionFactory.Create(), publisher, ambient);
}
