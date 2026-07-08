using CargoTracker.Shipper.Domain.Model;
using CargoTracker.Shipper.Domain.Model.Events;
using FluentAssertions;

namespace CargoTracker.Domain.Tests.Shipper;

public class ShipperTest
{
    [Fact]
    public void 個人荷主を登録でき割引率はゼロで荷主コードが自動採番される()
    {
        // When: 個人荷主を登録する
        var shipper = CargoTracker.Shipper.Domain.Model.Shipper.RegisterIndividual(
            new ShipperName("山田太郎"), new Email("yamada@example.com"), new Phone("03-1234-5678"));

        // Then
        shipper.Type.Should().Be(ShipperType.Individual);
        shipper.DiscountRate.Value.Should().Be(0m);
        shipper.ContractNumber.Should().BeNull();
        shipper.Code.Value.Should().StartWith("SHP-");
    }

    [Fact]
    public void 法人荷主を登録でき契約番号と割引率を保持する()
    {
        // When: 法人荷主を登録する
        var shipper = CargoTracker.Shipper.Domain.Model.Shipper.RegisterCorporate(
            new ShipperName("株式会社サンプル"), new Email("corp@example.com"), null,
            new ContractNumber("C-0001"), new DiscountRate(0.15m));

        // Then
        shipper.Type.Should().Be(ShipperType.Corporate);
        shipper.ContractNumber!.Value.Should().Be("C-0001");
        shipper.DiscountRate.Value.Should().Be(0.15m);
    }

    [Fact]
    public void 荷主登録時にドメインイベントが発生する()
    {
        var shipper = CargoTracker.Shipper.Domain.Model.Shipper.RegisterIndividual(
            new ShipperName("山田太郎"), new Email("yamada@example.com"), null);

        shipper.PullDomainEvents().Should().ContainSingle(e => e is ShipperRegisteredEvent);
    }

    [Theory]
    [InlineData(-0.0001)]
    [InlineData(0.3001)]
    [InlineData(1.0)]
    public void 割引率が0から30パーセントの範囲外なら例外(decimal invalidRate)
    {
        var act = () => new DiscountRate(invalidRate);
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Theory]
    [InlineData(0.0)]
    [InlineData(0.15)]
    [InlineData(0.30)]
    public void 割引率が0から30パーセントの範囲内なら生成できる(decimal validRate)
    {
        var rate = new DiscountRate(validRate);
        rate.Value.Should().Be(validRate);
    }

    [Fact]
    public void 不正なメールアドレスは例外()
    {
        var act = () => new Email("invalid-email");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void 荷主名が空なら例外()
    {
        var act = () => new ShipperName("  ");
        act.Should().Throw<ArgumentException>();
    }
}
