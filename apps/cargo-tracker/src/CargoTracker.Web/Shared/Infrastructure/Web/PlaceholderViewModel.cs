namespace CargoTracker.Shared.Infrastructure.Web;

/// <summary>
/// ウォーキングスケルトンの「準備中」プレースホルダ画面のモデル。
/// 実画面へ差し替えるまでの間、ナビゲーション到達性とロール制御を成立させる。
/// </summary>
public sealed record PlaceholderViewModel(string Title, string Note);
