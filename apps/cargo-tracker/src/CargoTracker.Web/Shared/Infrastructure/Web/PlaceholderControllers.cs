using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Shared.Infrastructure.Web;

// ウォーキングスケルトンのプレースホルダ群（ui_design 画面遷移図・ナビゲーション構成）。
// 各フローのランディングルートにロール制御付きで「準備中」画面を返す。担当 IT で実画面に差し替える。
// 表示ロールは ui_design のナビゲーション構成に従う（US26 受入条件 6）。

// 貨物追跡（/tracking）は TrackingController、荷役管理（/handling）は HandlingController で
// IT5 に実画面化済みのためプレースホルダを撤去した。

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
