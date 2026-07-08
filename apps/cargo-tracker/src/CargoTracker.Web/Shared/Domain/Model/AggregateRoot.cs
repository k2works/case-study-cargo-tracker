using MediatR;

namespace CargoTracker.Shared.Domain.Model;

/// <summary>
/// 集約ルートの基底クラス。ドメインイベントを蓄積し、Unit of Work のコミット成功後に
/// 回収・発行させる（post-commit ディスパッチ。ADR-0002）。
/// コミット前にイベントが観測される事故を構造的に防止する。
/// </summary>
public abstract class AggregateRoot
{
    private readonly List<INotification> _domainEvents = [];

    /// <summary>ドメイン操作の結果として発生したイベントを蓄積する。</summary>
    protected void AddDomainEvent(INotification domainEvent) => _domainEvents.Add(domainEvent);

    /// <summary>蓄積済みイベントを回収してクリアする。二重発行を防ぐため回収は一度きり。</summary>
    public IReadOnlyList<INotification> PullDomainEvents()
    {
        var events = _domainEvents.ToArray();
        _domainEvents.Clear();
        return events;
    }
}
