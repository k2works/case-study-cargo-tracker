using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;

namespace CargoTracker.Infrastructure.Tests.Shared;

public sealed class EnumDbCodecTest
{
    public enum SampleStatus
    {
        Pending,
        RouteProposed,
        CustomsHold,
    }

    [Theory]
    [InlineData(SampleStatus.Pending, "PENDING")]
    [InlineData(SampleStatus.RouteProposed, "ROUTE_PROPOSED")]
    [InlineData(SampleStatus.CustomsHold, "CUSTOMS_HOLD")]
    public void enum値をScreamingSnakeへ変換する(SampleStatus value, string expected)
    {
        EnumDbCodec.ToScreamingSnake(value).Should().Be(expected);
    }

    [Theory]
    [InlineData("PENDING", SampleStatus.Pending)]
    [InlineData("ROUTE_PROPOSED", SampleStatus.RouteProposed)]
    [InlineData("CUSTOMS_HOLD", SampleStatus.CustomsHold)]
    public void ScreamingSnakeをenum値へ復元する(string dbValue, SampleStatus expected)
    {
        EnumDbCodec.FromScreamingSnake<SampleStatus>(dbValue).Should().Be(expected);
    }

    [Theory]
    [InlineData(SampleStatus.Pending)]
    [InlineData(SampleStatus.RouteProposed)]
    [InlineData(SampleStatus.CustomsHold)]
    public void 往復変換で元の値に戻る(SampleStatus value)
    {
        var roundTrip = EnumDbCodec.FromScreamingSnake<SampleStatus>(EnumDbCodec.ToScreamingSnake(value));
        roundTrip.Should().Be(value);
    }
}
