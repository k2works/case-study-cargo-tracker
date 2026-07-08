namespace CargoTracker.Shared.Domain.Model;

/// <summary>荷主識別子（共有カーネル）。Guid ベースで Booking / Shipper コンテキストが共有する。</summary>
public sealed record ShipperId(Guid Value)
{
    public static ShipperId New() => new(Guid.NewGuid());
}
