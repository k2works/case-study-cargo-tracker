using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Shared.Infrastructure.Web;

// ウォーキングスケルトンのプレースホルダ群（ui_design 画面遷移図・ナビゲーション構成）。
// 各フローのランディングルートにロール制御付きで「準備中」画面を返す。担当 IT で実画面に差し替える。
// 表示ロールは ui_design のナビゲーション構成に従う（US26 受入条件 6）。

[Authorize(Roles = $"{Roles.Tracker}")]
public sealed class TrackingPlaceholderController : Controller
{
    [HttpGet("/tracking")]
    public IActionResult Index() =>
        View("Placeholder", new PlaceholderViewModel("貨物追跡", "担当 IT: IT5 / US14-18"));
}

[Authorize(Roles = $"{Roles.Handler},{Roles.Tracker}")]
public sealed class HandlingPlaceholderController : Controller
{
    [HttpGet("/handling")]
    public IActionResult Index() =>
        View("Placeholder", new PlaceholderViewModel("荷役管理", "担当 IT: IT5 / US15-16"));
}

[Authorize(Roles = $"{Roles.Billing}")]
public sealed class BillingPlaceholderController : Controller
{
    [HttpGet("/billing/invoices")]
    public IActionResult Index() =>
        View("Placeholder", new PlaceholderViewModel("請求管理", "担当 IT: IT7 / US21-23"));
}

[Authorize(Roles = $"{Roles.Admin}")]
public sealed class AdminPlaceholderController : Controller
{
    [HttpGet("/admin/discount-policies")]
    public IActionResult Index() =>
        View("Placeholder", new PlaceholderViewModel("管理設定", "担当 IT: IT7 / US22・割引ポリシー"));
}
