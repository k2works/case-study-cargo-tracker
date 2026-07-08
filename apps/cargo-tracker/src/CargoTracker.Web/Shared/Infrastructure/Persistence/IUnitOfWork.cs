using System.Data;
using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// トランザクションと蓄積ドメインイベントを所有する作業単位（ADR-0002）。
/// コマンドサービスは本ポート経由でリポジトリ操作を行い、<see cref="CommitAsync"/> の
/// コミット成功後に追跡集約のイベントを回収して発行する（post-commit ディスパッチ）。
/// </summary>
public interface IUnitOfWork : IAsyncDisposable
{
    /// <summary>リポジトリ操作が参加する現在のトランザクション。</summary>
    IDbTransaction Transaction { get; }

    /// <summary>コミット成功後にイベント回収の対象とする集約を登録する。</summary>
    void Track(AggregateRoot aggregate);

    /// <summary>トランザクションをコミットし、成功後に追跡集約のイベントを発行する。</summary>
    Task CommitAsync(CancellationToken ct = default);
}
