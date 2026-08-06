using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Booking.Application.Internal.Services;

/// <summary>
/// 現行 data-model は cargo.shipper_id が shipper.id（BIGINT）を参照する一方、共有 ShipperId は Guid 型。
/// M1 でポート境界を見直すまで、Booking の ACL 境界に閉じてサロゲート ID を可逆エンコードする。
/// </summary>
public static class ShipperIdCodec
{
    public static ShipperId FromSurrogateId(long id)
    {
        if (id <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(id), "荷主 ID は正の値でなければなりません。");
        }

        var bytes = new byte[16];
        BitConverter.GetBytes(id).CopyTo(bytes, 8);
        return new ShipperId(new Guid(bytes));
    }

    public static long ToSurrogateId(ShipperId id)
    {
        var bytes = id.Value.ToByteArray();
        return BitConverter.ToInt64(bytes, 8);
    }
}
