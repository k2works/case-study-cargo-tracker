using System.Data;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// DI スコープ内の現在のトランザクションを保持する。
/// リポジトリ実装は UoW から直接 IDbTransaction を受け取らず、このスコープ内の値を参照する。
/// </summary>
public sealed class AmbientTransaction
{
    public IDbTransaction? Current { get; set; }

    public IDbTransaction Require() =>
        Current ?? throw new InvalidOperationException("アクティブなトランザクションがありません。");
}
