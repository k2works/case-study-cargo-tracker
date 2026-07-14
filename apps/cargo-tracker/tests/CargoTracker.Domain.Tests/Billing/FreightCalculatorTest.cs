using CargoTracker.Billing.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Billing;

public class FreightCalculatorTest
{
    [Fact]
    public void 一般貨物は重量単価で基本料金を算出する()
    {
        // 100kg × 100 円/kg × 1.0 = 10,000 円
        FreightCalculator.CalculateBaseAmount(100m, "General")
            .Should().Be(new Money(10000, "JPY"));
    }

    [Fact]
    public void 危険物は割増係数が適用される()
    {
        // 100kg × 100 × 1.5 = 15,000 円
        FreightCalculator.CalculateBaseAmount(100m, "Hazardous")
            .Should().Be(new Money(15000, "JPY"));
    }

    [Fact]
    public void 冷凍貨物は割増係数が適用される()
    {
        // 100kg × 100 × 1.3 = 13,000 円
        FreightCalculator.CalculateBaseAmount(100m, "Refrigerated")
            .Should().Be(new Money(13000, "JPY"));
    }

    [Fact]
    public void 重量がゼロ以下なら算出できない()
    {
        var act = () => FreightCalculator.CalculateBaseAmount(0m, "General");

        act.Should().Throw<ArgumentOutOfRangeException>();
    }
}
