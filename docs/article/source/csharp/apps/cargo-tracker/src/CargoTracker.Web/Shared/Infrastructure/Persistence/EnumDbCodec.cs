namespace CargoTracker.Shared.Infrastructure.Persistence;

/// <summary>
/// 列挙型と DB 文字列（SCREAMING_SNAKE_CASE を正準）の相互変換を担う共通コーデック。
/// enum 名（PascalCase）を SCREAMING_SNAKE へ、またその逆へ変換する往復ペアを 1 箇所に集約し、
/// BC 横断で表現の不一致・往復整合の破綻を防ぐ（IT5 Try T1・IT6 レビュー H2）。
/// </summary>
public static class EnumDbCodec
{
    /// <summary>enum 値を SCREAMING_SNAKE_CASE の DB 文字列へ変換する（例: RouteProposed → ROUTE_PROPOSED）。</summary>
    public static string ToScreamingSnake<TEnum>(TEnum value) where TEnum : struct, Enum
    {
        var name = value.ToString()!;
        var builder = new System.Text.StringBuilder(name.Length + 4);
        for (var i = 0; i < name.Length; i++)
        {
            if (i > 0 && char.IsUpper(name[i]))
            {
                builder.Append('_');
            }
            builder.Append(char.ToUpperInvariant(name[i]));
        }
        return builder.ToString();
    }

    /// <summary>SCREAMING_SNAKE_CASE の DB 文字列を enum 値へ復元する（例: ROUTE_PROPOSED → RouteProposed）。</summary>
    public static TEnum FromScreamingSnake<TEnum>(string dbValue) where TEnum : struct, Enum
    {
        // アンダースコア除去 + 大文字小文字無視で enum 名に一致させる（PascalCase の内部区切りは無視）。
        var normalized = dbValue.Replace("_", string.Empty);
        return Enum.Parse<TEnum>(normalized, ignoreCase: true);
    }
}
