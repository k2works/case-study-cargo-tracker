# ADR-0002: Unit of Work と post-commit ドメインイベントディスパッチ

トランザクション境界の所有者として Unit of Work を導入し、ドメインイベントはコミット成功後にディスパッチする。

日付: 2026-07-04

## ステータス

2026-07-04 承認されました

## コンテキスト

architecture_backend.md は「集約にドメインイベントを蓄積し、コミット成功後にディスパッチする」方針を掲げているが、その実現機構（イベントの蓄積方法・トランザクションの所有者・ディスパッチの配線）が未定義だった。設計レビュー（2026-07-04）で、コード例がコミット前のインライン `Publish` になっており方針と矛盾していること、post-commit 保証を検証するテストが存在しないことが指摘された。

## 決定

1. **集約ルート基底クラスにイベント蓄積を持たせる**。

```csharp
public abstract class AggregateRoot
{
    private readonly List<INotification> _domainEvents = [];
    protected void AddDomainEvent(INotification e) => _domainEvents.Add(e);
    public IReadOnlyList<INotification> PullDomainEvents()
    {
        var events = _domainEvents.ToArray();
        _domainEvents.Clear();
        return events;
    }
}
```

2. **Unit of Work がトランザクションとイベントを所有する**。コマンドサービスは `IUnitOfWork` を受け取り、リポジトリ操作は UoW のトランザクション内で行う。`CommitAsync` はコミット成功後に、登録された集約からイベントを回収して MediatR `IPublisher` に発行する。

```csharp
public interface IUnitOfWork : IAsyncDisposable
{
    IDbTransaction Transaction { get; }
    void Track(AggregateRoot aggregate);          // イベント回収対象を登録
    Task CommitAsync(CancellationToken ct);       // commit 成功後に Publish
}
```

3. **コマンドサービスの標準フロー**: 集約取得 → ドメイン操作（集約内で `AddDomainEvent`）→ リポジトリ保存 → `CommitAsync`。サービス内での直接 `IPublisher.Publish` は禁止する。
4. **イベントハンドラは別トランザクション**。ハンドラ内の永続化は自身の UoW を開く。ハンドラ失敗はログと再試行（将来は Transactional Outbox へ移行）で扱い、元のコミットには影響させない。

## 影響

- コミット前にイベントが観測される事故（ロールバックしたのに通知が飛ぶ等）を構造的に防止できる
- ハンドラ失敗時にイベントが失われるリスクは残る（at-most-once）。高信頼が必要になった時点で Transactional Outbox パターンへ移行する（architecture_backend.md 記載の方針を維持）
- architecture_backend.md のコード例を本 ADR の方式に合わせて修正する
- **【追記 2026-07-14・ADR-0009】** 1 イベントが複数ハンドラを各独立 UoW で処理する連鎖（IT5 の荷役同期・IT6 の例外通知）では、部分適用による状態の恒久乖離が起こりうる。これを結果整合性モデルで扱い、ハンドラの冪等実装・同期失敗ログ・手動修復・Outbox 移行方針を [ADR-0009](0009-post-commitイベント連鎖の結果整合性方針.md) に定義する。本 ADR の「別トランザクション」方針の帰結として ADR-0009 を参照すること

## コンプライアンス

- 統合テストで「ロールバック時にイベントが発行されないこと」「コミット成功後にのみハンドラが呼ばれること」を検証する（test_strategy.md に追加）
- ArchUnitNET または コードレビューで、コマンドサービスから `IPublisher` への直接依存がないことを確認する

## 備考

- 起票: 設計レビュー 2026-07-04（提案 #6、xp-architect / xp-tester 指摘）
- 関連: ADR-0001（集約永続化戦略）
