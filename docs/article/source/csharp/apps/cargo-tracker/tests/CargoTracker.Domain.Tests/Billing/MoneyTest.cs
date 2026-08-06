using CargoTracker.Billing.Domain.Model;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Billing;

public class MoneyTest
{
    [Fact]
    public void 同一通貨の金額を加算できる()
    {
        var a = new Money(1000, "JPY");
        var b = new Money(500, "JPY");

        a.Add(b).Should().Be(new Money(1500, "JPY"));
    }

    [Fact]
    public void 通貨が異なる金額は加算できない()
    {
        var jpy = new Money(1000, "JPY");
        var usd = new Money(10, "USD");

        var act = () => jpy.Add(usd);

        act.Should().Throw<InvalidOperationException>();
    }

    [Fact]
    public void 負の金額は許可されない()
    {
        var act = () => new Money(-1, "JPY");

        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Theory]
    [InlineData(1000, 0.10, 900)]  // 10% 割引
    [InlineData(1000, 0.30, 700)]  // 上限 30% 割引
    [InlineData(1000, 0.00, 1000)] // 割引なし
    public void 割引率を適用し割引後金額を返す(long baseAmount, double rate, long expected)
    {
        var money = new Money(baseAmount, "JPY");

        money.ApplyDiscountRate((decimal)rate).Should().Be(new Money(expected, "JPY"));
    }

    [Fact]
    public void 乗算は最小通貨単位へ銀行家丸めされる()
    {
        // 銀行家丸め（ToEven）: 2.5 → 2、3.5 → 4
        new Money(5, "JPY").Multiply(0.5m).Should().Be(new Money(2, "JPY"));
        new Money(7, "JPY").Multiply(0.5m).Should().Be(new Money(4, "JPY"));
    }
}
