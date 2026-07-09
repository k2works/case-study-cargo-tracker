using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Shared.Application.Persistence;

/// <summary>
/// トランザクションと蓄積ドメインイベントを所有する作業単位（ADR-0002）。
/// コマンドサービスは本ポート経由でリポジトリ操作を行い、<see cref="CommitAsync"/> の
/// コミット成功後に追跡集約のイベントを回収して発行する（post-commit ディスパッチ）。
/// </summary>
public interface IUnitOfWork : IAsyncDisposable
{
    /// <summary>コミット成功後にイベント回収の対象とする集約を登録する。</summary>
    void Track(AggregateRoot aggregate);

    /// <summary>トランザクションをコミットし、成功後に追跡集約のイベントを発行する。</summary>
    Task CommitAsync(CancellationToken ct = default);
}

/// <summary>作業単位（<see cref="IUnitOfWork"/>）を生成するファクトリ。ユースケース単位で 1 つ開始する。</summary>
public interface IUnitOfWorkFactory
{
    IUnitOfWork Begin();
}
