namespace CargoTracker.Shared.Domain.Model;

/// <summary>
/// 位置情報（共有カーネル）。UN/LOCODE（国際規格の 5 文字コード）で港湾・地点を識別する。
/// 全コンテキストで共有する（domain-model・Shared Kernel）。
/// </summary>
public sealed record Location
{
    public string UnLocode { get; }
    public string Name { get; }

    public Location(string unLocode, string name = "")
    {
        if (string.IsNullOrWhiteSpace(unLocode) || unLocode.Length != 5)
        {
            throw new ArgumentException($"UN/LOCODE は 5 文字で指定してください: {unLocode}", nameof(unLocode));
        }
        UnLocode = unLocode.ToUpperInvariant();
        Name = name;
    }

    public bool SameAs(Location other) => UnLocode == other.UnLocode;
}
