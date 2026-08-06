using CargoTracker.Shared.Domain.Model;
using FluentAssertions;
using MediatR;

namespace CargoTracker.Domain.Tests.Shared;

public class AggregateRootTest
{
    private sealed record SampleEvent(string Name) : INotification;

    private sealed class SampleAggregate : AggregateRoot
    {
        public void DoSomething(string name) => AddDomainEvent(new SampleEvent(name));
    }

    [Fact]
    public void 発生したドメインイベントを蓄積して回収できる()
    {
        // Given: 集約でドメイン操作を 2 回実行する
        var aggregate = new SampleAggregate();
        aggregate.DoSomething("first");
        aggregate.DoSomething("second");

        // When: イベントを回収する
        var events = aggregate.PullDomainEvents();

        // Then: 蓄積順にイベントが取得できる
        events.Should().HaveCount(2);
        events.Should().AllBeOfType<SampleEvent>();
    }

    [Fact]
    public void 回収後はイベントがクリアされ二重発行されない()
    {
        // Given: イベントを 1 件蓄積した集約
        var aggregate = new SampleAggregate();
        aggregate.DoSomething("only");

        // When: 2 回回収する
        var first = aggregate.PullDomainEvents();
        var second = aggregate.PullDomainEvents();

        // Then: 1 回目のみ取得でき、2 回目は空（コミット前観測事故の構造的防止・ADR-0002）
        first.Should().HaveCount(1);
        second.Should().BeEmpty();
    }

    [Fact]
    public void 初期状態ではドメインイベントを持たない()
    {
        // Given & When: 新規集約
        var aggregate = new SampleAggregate();

        // Then: イベントは空
        aggregate.PullDomainEvents().Should().BeEmpty();
    }
}
