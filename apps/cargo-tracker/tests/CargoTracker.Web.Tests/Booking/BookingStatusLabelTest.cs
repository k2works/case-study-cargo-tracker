using CargoTracker.Booking.Interfaces;
using FluentAssertions;

namespace CargoTracker.Web.Tests.Booking;

public sealed class BookingStatusLabelTest
{
    [Theory]
    [InlineData("PRELIMINARY", "仮受付")]
    [InlineData("ROUTE_PROPOSED", "経路提案中")]
    [InlineData("CONFIRMED", "予約確定")]
    [InlineData("CANCELLED", "キャンセル")]
    public void 予約状態を日本語ラベルに変換する(string status, string expected)
        => BookingStatusLabel.ToJapanese(status).Should().Be(expected);

    [Fact]
    public void 未知の状態はそのまま返す()
        => BookingStatusLabel.ToJapanese("UNKNOWN").Should().Be("UNKNOWN");

    [Theory]
    [InlineData("ROUTE_PROPOSED", "info")]
    [InlineData("CONFIRMED", "success")]
    [InlineData("CANCELLED", "danger")]
    public void 状態に応じたバッジ色を返す(string status, string expected)
        => BookingStatusLabel.ToBadgeColor(status).Should().Be(expected);
}
