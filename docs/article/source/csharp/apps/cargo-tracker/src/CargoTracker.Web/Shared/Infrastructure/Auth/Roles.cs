namespace CargoTracker.Shared.Infrastructure.Auth;

/// <summary>
/// ロール定数（US26）。ロール別アクセス制御とナビゲーション表示に使用する。
/// 値は ASP.NET Core の認可（[Authorize(Roles = ...)]）で用いるロール名。
/// </summary>
public static class Roles
{
    public const string Admin = "ROLE_ADMIN";
    public const string Sales = "ROLE_SALES";
    public const string RouteDesigner = "ROLE_ROUTE_DESIGNER";
    public const string Tracker = "ROLE_TRACKER";
    public const string Handler = "ROLE_HANDLER";
    public const string Billing = "ROLE_BILLING";

    public static readonly IReadOnlyList<string> All =
    [
        Admin, Sales, RouteDesigner, Tracker, Handler, Billing,
    ];
}
