using System.Data;

namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// DI スコープ内の現在のトランザクションを保持する（ADR-0006）。
/// リポジトリ実装は UoW から直接 IDbTransaction を受け取らず、このスコープ内の値を参照する。
/// 本方式は「DI スコープ（= リクエスト）内では単一・非ネスト・非並行のトランザクション」を前提とする。
/// ネスト（既に Current がある状態での再開始）は前提違反として例外にし、静かな破壊を防ぐ（IT2 レビュー H2）。
/// </summary>
public sealed class AmbientTransaction
{
    public IDbTransaction? Current { get; private set; }

    /// <summary>現在のトランザクションを設定する。既に設定済みの場合はネスト前提違反として例外を投げる。</summary>
    public void Begin(IDbTransaction transaction)
    {
        if (Current is not null)
        {
            throw new InvalidOperationException(
                "アンビエントトランザクションは既に開始されています。スコープ内でのネスト・並行実行はサポートしていません（ADR-0006）。");
        }
        Current = transaction;
    }

    /// <summary>現在のトランザクションを解除する（UoW の破棄時に呼ぶ）。</summary>
    public void Clear() => Current = null;

    public IDbTransaction Require() =>
        Current ?? throw new InvalidOperationException("アクティブなトランザクションがありません。");
}
