using System.Globalization;
using CargoTracker.Booking.Application.Internal.CommandServices;
using CargoTracker.Booking.Application.Internal.OutboundServices;
using CargoTracker.Booking.Application.Internal.QueryServices;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Booking.Interfaces;

/// <summary>貨物予約登録・詳細・経路紐付け・通知・確定（US04/US11/US12/US13）。営業担当者のみ利用可。</summary>
[Authorize(Roles = Roles.Sales)]
public sealed class BookingController(
    FindBookingQueryService queryService,
    BookCargoCommandService commandService,
    AssignToRoutingCommandService assignToRoutingCommandService,
    RouteCargoCommandService routeCargoCommandService,
    NotifyRouteToShipperCommandService notifyRouteToShipperCommandService,
    ConfirmBookingCommandService confirmBookingCommandService,
    ReturnToRoutingCommandService returnToRoutingCommandService,
    CancelBookingCommandService cancelBookingCommandService,
    ISelectedRouteLookup selectedRouteLookup) : Controller
{
    [HttpGet("/bookings")]
    public IActionResult Index() => LocalRedirect("/bookings/new");

    [HttpGet("/bookings/new")]
    public async Task<IActionResult> New(CancellationToken ct)
        => View(await BuildFormAsync(new BookingForm(), ct));

    [HttpGet("/bookings/new/cargo-fields")]
    public IActionResult CargoFields(string? cargoType)
        => PartialView("_CargoFields", new BookingForm { CargoType = cargoType ?? nameof(CargoType.General) });

    [HttpPost("/bookings")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Create(BookingForm form, CancellationToken ct)
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
            return View("New", await BuildFormAsync(form, ct));
        }

        try
        {
            var bookingId = await commandService.HandleAsync(new BookCargoCommand(
                form.ShipperId, form.OriginUnLocode, form.DestinationUnLocode, form.ArrivalDeadline,
                cargoType, form.Weight, form.DimensionLength, form.DimensionWidth, form.DimensionHeight,
                form.Quantity, form.Description, form.HazardousClass, form.UnNumber, form.ProperShippingName,
                form.MinTemperature, form.MaxTemperature, form.TemperatureUnit), ct);
            TempData["SuccessMessage"] = "貨物予約を登録しました。";
            return LocalRedirect($"/bookings/{bookingId.Value}");
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            ModelState.AddModelError(string.Empty, ex.Message);
            return View("New", await BuildFormAsync(form, ct));
        }
    }

    [HttpGet("/bookings/{bookingId}")]
    public async Task<IActionResult> Show(string bookingId, CancellationToken ct)
    {
        var detail = await queryService.FindByBookingIdAsync(bookingId, ct);
        if (detail is null)
        {
            return NotFound();
        }
        return View(detail);
    }

    [HttpPost("/bookings/{bookingId}/assign-routing")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> AssignRouting(string bookingId, CancellationToken ct)
    {
        try
        {
            await assignToRoutingCommandService.HandleAsync(new AssignToRoutingCommand(new BookingId(bookingId)), ct);
            TempData["SuccessMessage"] = "経路設計を依頼しました。";
        }
        catch (InvalidOperationException ex)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/bookings/{bookingId}");
    }

    [HttpPost("/bookings/{bookingId}/route")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> RouteCargo(string bookingId, CancellationToken ct)
    {
        try
        {
            var legs = await selectedRouteLookup.FindLegsByBookingIdAsync(bookingId, ct);
            if (legs.Count == 0)
            {
                TempData["WarningMessage"] = "確定経路がありません。先に経路設計者が経路を選択・確定してください。";
                return LocalRedirect($"/bookings/{bookingId}");
            }

            var command = new RouteCargoCommand(bookingId, legs.Select(l => new RouteLegInput(
                l.VoyageNumber, l.LoadUnLocode, l.UnloadUnLocode, l.LoadTime, l.UnloadTime)).ToList());
            await routeCargoCommandService.HandleAsync(command, ct);
            TempData["SuccessMessage"] = "確定経路を予約に紐付けました。";
        }
        catch (Exception ex) when (ex is ArgumentException or InvalidOperationException)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/bookings/{bookingId}");
    }

    [HttpPost("/bookings/{bookingId}/notify")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Notify(string bookingId, CancellationToken ct)
    {
        try
        {
            await notifyRouteToShipperCommandService.HandleAsync(new NotifyRouteToShipperCommand(new BookingId(bookingId)), ct);
            TempData["SuccessMessage"] = "確定経路を荷主に通知しました。";
        }
        catch (InvalidOperationException ex)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/bookings/{bookingId}");
    }

    [HttpPost("/bookings/{bookingId}/confirm")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Confirm(string bookingId, CancellationToken ct)
    {
        try
        {
            await confirmBookingCommandService.HandleAsync(new ConfirmBookingCommand(new BookingId(bookingId)), ct);
            TempData["SuccessMessage"] = "予約を確定しました。";
        }
        catch (InvalidOperationException ex)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/bookings/{bookingId}");
    }

    [HttpPost("/bookings/{bookingId}/return-routing")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> ReturnRouting(string bookingId, CancellationToken ct)
    {
        try
        {
            await returnToRoutingCommandService.HandleAsync(new ReturnToRoutingCommand(new BookingId(bookingId)), ct);
            TempData["SuccessMessage"] = "経路再設計に差し戻しました。";
        }
        catch (InvalidOperationException ex)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/bookings/{bookingId}");
    }

    [HttpPost("/bookings/{bookingId}/cancel")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Cancel(string bookingId, CancellationToken ct)
    {
        try
        {
            await cancelBookingCommandService.HandleAsync(new CancelBookingCommand(new BookingId(bookingId)), ct);
            TempData["SuccessMessage"] = "予約をキャンセルしました。";
        }
        catch (InvalidOperationException ex)
        {
            TempData["WarningMessage"] = ex.Message;
        }

        return LocalRedirect($"/bookings/{bookingId}");
    }

    private async Task<BookingForm> BuildFormAsync(BookingForm form, CancellationToken ct)
    {
        form.Shippers = await queryService.FindShipperOptionsAsync(ct);
        if (string.IsNullOrWhiteSpace(form.ShipperId) && form.Shippers.Count > 0)
        {
            form.ShipperId = form.Shippers[0].ShipperId.ToString(CultureInfo.InvariantCulture);
        }
        return form;
    }
}
