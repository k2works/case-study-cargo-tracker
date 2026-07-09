namespace CargoTracker.Shared.Infrastructure.Seeding;

/// <summary>
/// シード投入設定（appsettings / 環境変数の "Seed" セクション）。
/// Development 以外のデプロイ環境（deploy:dev の Heroku など）でデモデータを投入したい場合に
/// <c>Seed__Enabled=true</c> を設定する。本番では false のままにする。
/// </summary>
public sealed class SeedOptions
{
    public const string SectionName = "Seed";

    public bool Enabled { get; set; }

    /// <summary>
    /// Routing のデモデータ（航海スケジュール・経路設計依頼）を投入するか。
    /// 既定 true。テスト環境ではデータ分離のため <c>Seed__IncludeRoutingDemo=false</c> で無効化する。
    /// </summary>
    public bool IncludeRoutingDemo { get; set; } = true;
}
