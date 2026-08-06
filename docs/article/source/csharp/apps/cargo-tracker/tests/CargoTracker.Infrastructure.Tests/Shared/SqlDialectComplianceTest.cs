using System.Text.RegularExpressions;
using CargoTracker.Shared.Infrastructure.Persistence;
using FluentAssertions;

namespace CargoTracker.Infrastructure.Tests.Shared;

/// <summary>
/// ADR-0003 #2 コンプライアンス: リポジトリ・クエリサービスの実行時 SQL に PostgreSQL 固有方言が
/// 混入していないことを、ソースの文字列リテラルを走査して検出する。二方言（SQLite/PostgreSQL）で
/// 動作させるため、禁止パターンをローカル・CI の単体テストで継続的に検証する。
/// </summary>
public class SqlDialectComplianceTest
{
    // 禁止する PostgreSQL 方言パターン（ADR-0003 #1 の禁止規約）。
    private static readonly (string Name, Regex Pattern)[] _forbidden =
    [
        ("NOW()", new Regex(@"\bNOW\s*\(", RegexOptions.IgnoreCase)),
        ("RETURNING", new Regex(@"\bRETURNING\b", RegexOptions.IgnoreCase)),
        ("ILIKE", new Regex(@"\bILIKE\b", RegexOptions.IgnoreCase)),
        ("ON CONFLICT", new Regex(@"\bON\s+CONFLICT\b", RegexOptions.IgnoreCase)),
        ("JSONB", new Regex(@"\bJSONB\b", RegexOptions.IgnoreCase)),
        (":: キャスト", new Regex("::", RegexOptions.None)),
    ];

    private static string FindWebProjectDir()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            var candidate = Path.Combine(dir.FullName, "src", "CargoTracker.Web", "CargoTracker.Web.csproj");
            if (File.Exists(candidate))
            {
                return Path.GetDirectoryName(candidate)!;
            }
            dir = dir.Parent;
        }
        throw new DirectoryNotFoundException("CargoTracker.Web プロジェクトの場所を特定できませんでした。");
    }

    /// <summary>C# ソースから文字列リテラル（生文字列 """...""" と通常の "..."）の中身のみを抽出する。</summary>
    private static string ExtractStringLiterals(string source)
    {
        var literals = new List<string>();
        foreach (Match match in Regex.Matches(source, "\"\"\"(.*?)\"\"\"", RegexOptions.Singleline))
        {
            literals.Add(match.Groups[1].Value);
        }
        // 生文字列を取り除いてから通常文字列を抽出する（二重取得を避ける）。
        var withoutRaw = Regex.Replace(source, "\"\"\".*?\"\"\"", " ", RegexOptions.Singleline);
        foreach (Match match in Regex.Matches(withoutRaw, "\"((?:\\\\.|[^\"\\\\])*)\""))
        {
            literals.Add(match.Groups[1].Value);
        }
        return string.Join("\n", literals);
    }

    [Fact]
    public void リポジトリとクエリのSQLにPostgreSQL固有方言が混入していない()
    {
        var webDir = FindWebProjectDir();

        // SQL を含む層（Infrastructure リポジトリ・Application クエリ・Auth）を走査対象とする。
        var sourceFiles = Directory
            .EnumerateFiles(webDir, "*.cs", SearchOption.AllDirectories)
            .Where(path => !path.Contains($"{Path.DirectorySeparatorChar}obj{Path.DirectorySeparatorChar}")
                        && !path.Contains($"{Path.DirectorySeparatorChar}bin{Path.DirectorySeparatorChar}"))
            .ToList();

        var violations = new List<string>();
        foreach (var file in sourceFiles)
        {
            // 方言検出テスト自身（禁止パターンを定義として含む）は対象外にする。
            if (Path.GetFileName(file) == nameof(SqlDialectComplianceTest) + ".cs")
            {
                continue;
            }

            var sqlText = ExtractStringLiterals(File.ReadAllText(file));
            foreach (var (name, pattern) in _forbidden)
            {
                if (pattern.IsMatch(sqlText))
                {
                    violations.Add($"{Path.GetFileName(file)} に禁止方言 '{name}' が含まれています。");
                }
            }
        }

        violations.Should().BeEmpty(
            "実行時 SQL は ADR-0003 の方言禁止規約に従う必要があります:\n" + string.Join("\n", violations));
    }

    [Fact]
    public void 検出ロジックが禁止パターンを実際に検出できる()
    {
        // 走査ロジックのメタテスト（false negative 防止）。
        var sample = ExtractStringLiterals("""
            var sql = "SELECT id FROM t WHERE created_at = NOW() RETURNING id";
            """);

        _forbidden.Should().Contain(f => f.Name == "NOW()" && f.Pattern.IsMatch(sample));
        _forbidden.Should().Contain(f => f.Name == "RETURNING" && f.Pattern.IsMatch(sample));
    }

    [Fact]
    public void スクリプト同期検証と併せて二方言コンプライアンスを担保する()
    {
        // ADR-0003 #3（スクリプト同期）は MigrationScriptSyncTest で担保。ここでは存在確認のみ。
        typeof(DatabaseMigrator).Assembly.GetManifestResourceNames()
            .Where(n => n.Contains(".Scripts.", StringComparison.Ordinal))
            .Should().NotBeEmpty();
    }
}
