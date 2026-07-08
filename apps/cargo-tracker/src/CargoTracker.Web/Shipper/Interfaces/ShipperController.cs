using CargoTracker.Shared.Infrastructure.Auth;
using CargoTracker.Shipper.Application.Internal.CommandServices;
using CargoTracker.Shipper.Application.Internal.QueryServices;
using CargoTracker.Shipper.Domain;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Shipper.Interfaces;

/// <summary>荷主一覧・登録画面（US02/US03）。営業担当者のみ利用可。</summary>
[Authorize(Roles = Roles.Sales)]
public sealed class ShipperController(
    FindShipperQueryService queryService,
    RegisterShipperCommandService commandService) : Controller
{
    [HttpGet("/shippers")]
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var shippers = await queryService.FindAllAsync(ct);
        return View(shippers);
    }

    [HttpGet("/shippers/new")]
    public IActionResult New() => View(new ShipperForm());

    [HttpPost("/shippers/new")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> New(ShipperForm form, CancellationToken ct)
    {
        // 法人選択時は契約番号・割引率を必須にする（種別依存の相関バリデーション）。
        if (form.IsCorporate && string.IsNullOrWhiteSpace(form.ContractNumber))
        {
            ModelState.AddModelError(nameof(form.ContractNumber), "法人荷主には契約番号が必要です。");
        }

        if (!ModelState.IsValid)
        {
            return View(form);
        }

        var command = new RegisterShipperCommand(
            IsCorporate: form.IsCorporate,
            Name: form.Name,
            Email: form.Email,
            Phone: form.Phone,
            Address: form.Address,
            ContractNumber: form.ContractNumber,
            DiscountRate: form.IsCorporate ? (form.DiscountPercent ?? 0m) / 100m : null);

        try
        {
            await commandService.HandleAsync(command, ct);
        }
        catch (EmailAlreadyRegisteredException)
        {
            ModelState.AddModelError(nameof(form.Email), "このメールアドレスは既に登録されています。");
            return View(form);
        }

        TempData["SuccessMessage"] = "荷主を登録しました。";
        return LocalRedirect("/shippers");
    }
}
