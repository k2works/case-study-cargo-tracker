using System.Text.RegularExpressions;

namespace CargoTracker.Shipper.Domain.Model;

/// <summary>荷主種別。</summary>
public enum ShipperType
{
    Individual,
    Corporate,
}

/// <summary>荷主コード（業務キー・SHP-XXXXXX 形式）。登録時に自動採番する。</summary>
public sealed record ShipperCode
{
    public string Value { get; }

    public ShipperCode(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("荷主コードは必須です。", nameof(value));
        }
        Value = value;
    }

    public static ShipperCode Generate() => new($"SHP-{Guid.NewGuid().ToString("N")[..6].ToUpperInvariant()}");
}

/// <summary>荷主名称（必須・最大 200 文字）。</summary>
public sealed record ShipperName
{
    public string Value { get; }

    public ShipperName(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("荷主名は必須です。", nameof(value));
        }
        if (value.Length > 200)
        {
            throw new ArgumentException("荷主名は 200 文字以内で入力してください。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>メールアドレス（必須・最大 200 文字・形式検証）。</summary>
public sealed partial record Email
{
    public string Value { get; }

    public Email(string value)
    {
        if (string.IsNullOrWhiteSpace(value) || !EmailPattern().IsMatch(value))
        {
            throw new ArgumentException($"メールアドレスの形式が不正です: {value}", nameof(value));
        }
        if (value.Length > 200)
        {
            throw new ArgumentException("メールアドレスは 200 文字以内で入力してください。", nameof(value));
        }
        Value = value;
    }

    [GeneratedRegex(@"^[^@\s]+@[^@\s]+\.[^@\s]+$")]
    private static partial Regex EmailPattern();
}

/// <summary>住所（任意・最大 500 文字）。</summary>
public sealed record Address
{
    public string Value { get; }

    public Address(string value)
    {
        if (value.Length > 500)
        {
            throw new ArgumentException("住所は 500 文字以内で入力してください。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>電話番号（任意・最大 50 文字）。</summary>
public sealed record Phone
{
    public string Value { get; }

    public Phone(string value)
    {
        if (value.Length > 50)
        {
            throw new ArgumentException("電話番号は 50 文字以内で入力してください。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>契約番号（法人のみ・最大 50 文字）。</summary>
public sealed record ContractNumber
{
    public string Value { get; }

    public ContractNumber(string value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("契約番号は必須です。", nameof(value));
        }
        if (value.Length > 50)
        {
            throw new ArgumentException("契約番号は 50 文字以内で入力してください。", nameof(value));
        }
        Value = value;
    }
}

/// <summary>割引率（0.0000〜0.3000、最大 30%）。個人荷主は 0。</summary>
public sealed record DiscountRate
{
    public decimal Value { get; }

    public DiscountRate(decimal value)
    {
        if (value is < 0.0000m or > 0.3000m)
        {
            throw new ArgumentOutOfRangeException(nameof(value), value, "割引率は 0〜30% の範囲でなければなりません。");
        }
        Value = value;
    }

    public static DiscountRate None { get; } = new(0m);
}
