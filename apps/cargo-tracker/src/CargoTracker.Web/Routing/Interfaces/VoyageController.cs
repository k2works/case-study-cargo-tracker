using CargoTracker.Routing.Application.Internal.CommandServices;
using CargoTracker.Routing.Application.Internal.QueryServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Routing.Interfaces;

/// <summary>航海スケジュール登録・一覧（US24）。経路設計者のみ利用可。</summary>
[Authorize(Roles = Roles.RouteDesigner)]
public sealed class VoyageController(
    FindVoyageQueryService queryService,
    RegisterVoyageCommandService commandService,
    UpdateScheduleCommandService updateCommandService) : Controller
{
    [HttpGet("/voyages")]
    public async Task<IActionResult> Index(CancellationToken ct)
        => View(await queryService.FindAllAsync(ct));

    [HttpGet("/voyages/new")]
    public IActionResult New()
        => View(BuildDefaultForm());

    [HttpGet("/voyages/new/movement-row")]
    [HttpGet("/voyages/movement-row")]
    public IActionResult MovementRow(int index)
        => PartialView("_MovementRow", new CarrierMovementRowForm(index, new CarrierMovementForm()));

    [HttpPost("/voyages")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Create(VoyageForm form, CancellationToken ct)
    {
        var supportedCargoTypes = ParseSupportedCargoTypes(form);
        ValidateForm(form, supportedCargoTypes);

        if (!ModelState.IsValid)
        {
            EnsureMovementRows(form);
            return View("New", form);
        }

        try
        {
            await commandService.HandleAsync(new RegisterVoyageCommand(
                form.VoyageNumber,
                form.VesselName,
                form.Carrier,
                supportedCargoTypes,
                form.CarrierMovements.Select((movement, index) => new RegisterCarrierMovementCommand(
                    movement.DepartureLocationUnLocode,
                    movement.ArrivalLocationUnLocode,
                    movement.DepartureDate!.Value,
                    movement.ArrivalDate!.Value,
                    index + 1)).ToArray()), ct);

            TempData["SuccessMessage"] = "航海スケジュールを登録しました。";
            return LocalRedirect("/voyages");
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            ModelState.AddModelError(string.Empty, ex.Message);
            EnsureMovementRows(form);
            return View("New", form);
        }
    }

    [HttpGet("/voyages/{voyageNumber}/edit")]
    public async Task<IActionResult> Edit(string voyageNumber, CancellationToken ct)
    {
        var existing = await queryService.FindByVoyageNumberAsync(voyageNumber, ct);
        if (existing is null)
        {
            return NotFound();
        }

        return View(new EditVoyageViewModel(BuildForm(existing), existing));
    }

    [HttpPost("/voyages/{voyageNumber}")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Update(string voyageNumber, VoyageForm form, CancellationToken ct)
    {
        form.VoyageNumber = voyageNumber;
        var existing = await queryService.FindByVoyageNumberAsync(voyageNumber, ct);
        if (existing is null)
        {
            return NotFound();
        }

        var supportedCargoTypes = ParseSupportedCargoTypes(form);
        ValidateForm(form, supportedCargoTypes);
        if (!ModelState.IsValid)
        {
            EnsureMovementRows(form);
            return View("Edit", new EditVoyageViewModel(form, existing));
        }

        try
        {
            await updateCommandService.HandleAsync(new UpdateScheduleCommand(
                voyageNumber,
                form.Version,
                form.VesselName,
                form.Carrier,
                supportedCargoTypes,
                form.CarrierMovements.Select((movement, index) => new RegisterCarrierMovementCommand(
                    movement.DepartureLocationUnLocode,
                    movement.ArrivalLocationUnLocode,
                    movement.DepartureDate!.Value,
                    movement.ArrivalDate!.Value,
                    index + 1)).ToArray()), ct);

            TempData["SuccessMessage"] = "航海スケジュールを更新しました。";
            return LocalRedirect("/voyages");
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            ModelState.AddModelError(string.Empty, ex.Message);
            EnsureMovementRows(form);
            return View("Edit", new EditVoyageViewModel(form, existing));
        }
    }

    private static VoyageForm BuildDefaultForm()
        => new()
        {
            SupportedCargoTypes = [nameof(SupportedCargoType.General)],
            CarrierMovements =
            [
                new CarrierMovementForm(),
            ],
        };

    private static VoyageForm BuildForm(VoyageDetail detail)
        => new()
        {
            VoyageNumber = detail.VoyageNumber,
            Version = detail.Version,
            VesselName = detail.VesselName,
            Carrier = detail.Carrier,
            SupportedCargoTypes = detail.SupportedCargoTypes
                .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                .Select(value => Enum.Parse<SupportedCargoType>(value, ignoreCase: true).ToString())
                .ToList(),
            CarrierMovements = detail.CarrierMovements
                .OrderBy(movement => movement.SequenceNumber)
                .Select(movement => new CarrierMovementForm
                {
                    DepartureLocationUnLocode = movement.DepartureLocationUnlocode,
                    ArrivalLocationUnLocode = movement.ArrivalLocationUnlocode,
                    DepartureDate = new DateTimeOffset(DateTime.SpecifyKind(movement.DepartureDate, DateTimeKind.Utc)),
                    ArrivalDate = new DateTimeOffset(DateTime.SpecifyKind(movement.ArrivalDate, DateTimeKind.Utc)),
                })
                .ToList(),
        };

    private static void EnsureMovementRows(VoyageForm form)
    {
        if (form.CarrierMovements.Count == 0)
        {
            form.CarrierMovements.Add(new CarrierMovementForm());
        }
    }

    private void ValidateForm(VoyageForm form, List<SupportedCargoType> supportedCargoTypes)
    {
        if (supportedCargoTypes.Count == 0)
        {
            ModelState.AddModelError(nameof(form.SupportedCargoTypes), "対応貨物種別を 1 つ以上選択してください。");
        }
        if (form.CarrierMovements.Count == 0)
        {
            ModelState.AddModelError(nameof(form.CarrierMovements), "運送区間を 1 件以上入力してください。");
        }

        for (var i = 0; i < form.CarrierMovements.Count; i++)
        {
            var movement = form.CarrierMovements[i];
            if (movement.DepartureDate is not null && movement.ArrivalDate is not null
                && movement.DepartureDate > movement.ArrivalDate)
            {
                ModelState.AddModelError(string.Empty, $"区間 {i + 1} の出発日時は到着日時以前でなければなりません。");
            }
            if (string.Equals(
                    movement.DepartureLocationUnLocode,
                    movement.ArrivalLocationUnLocode,
                    StringComparison.OrdinalIgnoreCase))
            {
                ModelState.AddModelError(string.Empty, $"区間 {i + 1} の出発港と到着港は異なる必要があります。");
            }
            if (i > 0 && !string.Equals(
                    form.CarrierMovements[i - 1].ArrivalLocationUnLocode,
                    movement.DepartureLocationUnLocode,
                    StringComparison.OrdinalIgnoreCase))
            {
                ModelState.AddModelError(string.Empty, $"区間 {i} の到着港と区間 {i + 1} の出発港が一致している必要があります。");
            }
        }
    }

    private List<SupportedCargoType> ParseSupportedCargoTypes(VoyageForm form)
    {
        var supportedCargoTypes = new List<SupportedCargoType>();
        foreach (var value in form.SupportedCargoTypes)
        {
            if (Enum.TryParse<SupportedCargoType>(value, out var cargoType))
            {
                supportedCargoTypes.Add(cargoType);
            }
            else
            {
                ModelState.AddModelError(nameof(form.SupportedCargoTypes), "対応貨物種別が不正です。");
            }
        }
        return supportedCargoTypes;
    }
}
