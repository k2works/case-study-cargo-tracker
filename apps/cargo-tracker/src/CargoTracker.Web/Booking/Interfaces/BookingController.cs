using System.Globalization;
using CargoTracker.Booking.Application.Internal.CommandServices;
using CargoTracker.Booking.Application.Internal.QueryServices;
using CargoTracker.Booking.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Booking.Interfaces;

/// <summary>貨物予約登録・詳細（US04）。営業担当者のみ利用可。</summary>
[Authorize(Roles = Roles.Sales)]
public sealed class BookingController(
    FindBookingQueryService queryService,
    BookCargoCommandService commandService) : Controller
{
    [HttpGet("/bookings")]
    public IActionResult Index() => LocalRedirect("/bookings/new");

    [HttpGet("/bookings/new")]
    public async Task<IActionResult> New(CancellationToken ct)
        => View(await BuildFormAsync(new BookingForm(), ct));

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
                form.Quantity, form.Description), ct);
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
