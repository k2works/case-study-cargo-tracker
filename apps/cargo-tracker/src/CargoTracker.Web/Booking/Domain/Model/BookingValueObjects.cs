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

/// <summary>危険物申告。危険物貨物の予約時に必須となる。</summary>
public sealed record HazardousDeclaration
{
    public string HazardousClass { get; }
    public string UnNumber { get; }
    public string ProperShippingName { get; }

    public HazardousDeclaration(string hazardousClass, string unNumber, string properShippingName)
    {
        if (string.IsNullOrWhiteSpace(hazardousClass))
        {
            throw new ArgumentException("危険物クラスは必須です。", nameof(hazardousClass));
        }
        if (string.IsNullOrWhiteSpace(unNumber))
        {
            throw new ArgumentException("UN 番号は必須です。", nameof(unNumber));
        }
        if (string.IsNullOrWhiteSpace(properShippingName))
        {
            throw new ArgumentException("正式輸送品名は必須です。", nameof(properShippingName));
        }
        HazardousClass = hazardousClass;
        UnNumber = unNumber;
        ProperShippingName = properShippingName;
    }
}

/// <summary>温度単位。</summary>
public enum TemperatureUnit
{
    Celsius,
    Fahrenheit,
}

/// <summary>温度管理条件。冷凍・冷蔵貨物の予約時に必須となる。</summary>
public sealed record TemperatureRequirement
{
    public decimal MinTemperature { get; }
    public decimal MaxTemperature { get; }
    public TemperatureUnit TemperatureUnit { get; }

    public TemperatureRequirement(decimal minTemperature, decimal maxTemperature, TemperatureUnit temperatureUnit)
    {
        if (minTemperature > maxTemperature)
        {
            throw new ArgumentException("最低温度は最高温度以下でなければなりません。", nameof(minTemperature));
        }
        MinTemperature = minTemperature;
        MaxTemperature = maxTemperature;
        TemperatureUnit = temperatureUnit;
    }
}

/// <summary>
/// 航海番号（Booking BC 固有）。Routing BC の VoyageNumber とは独立に定義する（ADR-0007 の BC 独立方針）。
/// 経路紐付け ACL（US11）が Routing の確定経路を Booking の旅程に変換する際に用いる。
/// </summary>
public sealed record VoyageNumber
{
    public string Value { get; }

    public VoyageNumber(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("航海番号は必須です。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>旅程の 1 区間。特定の航海で積地から揚地まで輸送する単位。</summary>
public sealed record Leg(
    VoyageNumber Voyage,
    Location LoadLocation,
    Location UnloadLocation,
    DateTimeOffset LoadTime,
    DateTimeOffset UnloadTime);

/// <summary>
/// 旅程（US11）。1 つ以上の Leg で構成される貨物の輸送経路全体。
/// Leg 連結制約（Leg[n].UnloadLocation == Leg[n+1].LoadLocation）を不変条件として持つ。
/// </summary>
public sealed record CargoItinerary
{
    public IReadOnlyList<Leg> Legs { get; }

    public CargoItinerary(IReadOnlyList<Leg> legs)
    {
        if (legs is null || legs.Count == 0)
        {
            throw new ArgumentException("旅程は 1 区間以上で構成されます。", nameof(legs));
        }
        for (var i = 0; i < legs.Count - 1; i++)
        {
            if (!legs[i].UnloadLocation.Equals(legs[i + 1].LoadLocation))
            {
                throw new ArgumentException(
                    "旅程の区間は連結していなければなりません（前区間の揚地と次区間の積地が一致）。", nameof(legs));
            }
        }
        Legs = legs;
    }

    /// <summary>到着予定時刻（最終区間の揚地時刻）。</summary>
    public DateTimeOffset ExpectedArrivalTime => Legs[^1].UnloadTime;
}
