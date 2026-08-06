using CargoTracker.Estimation.Application.Internal.CommandServices;
using CargoTracker.Estimation.Application.Internal.QueryServices;
using CargoTracker.Estimation.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Estimation.Interfaces;

/// <summary>見積の一覧・作成・詳細（US01）。営業担当者のみ利用可。</summary>
[Authorize(Roles = Roles.Sales)]
public sealed class EstimateController(
    FindEstimateQueryService queryService,
    CreateEstimateCommandService commandService) : Controller
{
    [HttpGet("/estimates")]
    public async Task<IActionResult> Index(CancellationToken ct)
    {
        var estimates = await queryService.FindAllAsync(ct);
        return View(estimates);
    }

    [HttpGet("/estimates/new")]
    public IActionResult New() => View(new EstimateForm());

    /// <summary>貨物種別に応じた危険物申告フォームの部分ビューを返す（US01 受入条件 6・htmx 動的切替）。</summary>
    [HttpGet("/estimates/hazardous-declaration")]
    public IActionResult HazardousDeclaration(string? cargoType)
        => PartialView("_HazardousDeclaration", cargoType ?? "General");

    [HttpPost("/estimates/new")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> New(EstimateForm form, CancellationToken ct)
    {
        if (!Enum.TryParse<CargoType>(form.CargoType, out var cargoType))
        {
            ModelState.AddModelError(nameof(form.CargoType), "貨物種別が不正です。");
        }
        if (string.Equals(form.OriginUnLocode, form.DestinationUnLocode, StringComparison.OrdinalIgnoreCase))
        {
            ModelState.AddModelError(nameof(form.DestinationUnLocode), "出発地と仕向地は異なる必要があります。");
        }

        if (!ModelState.IsValid)
        {
            return View(form);
        }

        var estimateId = await commandService.HandleAsync(new CreateEstimateCommand(
            form.OriginUnLocode, form.DestinationUnLocode, form.ArrivalDeadline, cargoType, form.WeightKg), ct);

        TempData["SuccessMessage"] = "見積を作成しました。";
        return LocalRedirect($"/estimates/{estimateId.Value}");
    }

    [HttpGet("/estimates/{estimateId:guid}")]
    public async Task<IActionResult> Show(Guid estimateId, CancellationToken ct)
    {
        var detail = await queryService.FindByEstimateIdAsync(estimateId, ct);
        if (detail is null)
        {
            return NotFound();
        }
        return View(detail);
    }
}
