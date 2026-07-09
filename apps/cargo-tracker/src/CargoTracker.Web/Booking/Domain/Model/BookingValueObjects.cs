using CargoTracker.Shared.Domain.Model;

namespace CargoTracker.Booking.Domain.Model;

/// <summary>貨物寸法（cm）。指定する場合は三辺すべて正の値。</summary>
public sealed record Dimensions
{
    public decimal Length { get; }
    public decimal Width { get; }
    public decimal Height { get; }

    public Dimensions(decimal length, decimal width, decimal height)
    {
        if (length <= 0 || width <= 0 || height <= 0)
        {
            throw new ArgumentException("寸法はすべて正の値でなければなりません。");
        }
        Length = length;
        Width = width;
        Height = height;
    }
}

/// <summary>貨物個数。指定する場合は 1 以上。</summary>
public sealed record Quantity
{
    public int Value { get; }

    public Quantity(int value)
    {
        if (value < 1)
        {
            throw new ArgumentException("個数は 1 以上でなければなりません。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>品名。最大 500 文字。</summary>
public sealed record Description
{
    public string Value { get; }

    public Description(string value)
    {
        if (value.Length > 500)
        {
            throw new ArgumentException("品名は 500 文字以内で入力してください。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>輸送条件。出発地・仕向地・到着期限を保持する。</summary>
public sealed record RouteSpecification
{
    public Location Origin { get; }
    public Location Destination { get; }
    public DateOnly ArrivalDeadline { get; }

    public RouteSpecification(Location origin, Location destination, DateOnly arrivalDeadline)
    {
        if (origin.SameAs(destination))
        {
            throw new ArgumentException("出発地と仕向地は異なる必要があります。", nameof(destination));
        }
        Origin = origin;
        Destination = destination;
        ArrivalDeadline = arrivalDeadline;
    }
}
