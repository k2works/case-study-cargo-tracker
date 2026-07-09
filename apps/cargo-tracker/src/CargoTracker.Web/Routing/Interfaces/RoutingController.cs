using CargoTracker.Routing.Application.Internal.OutboundServices;
using CargoTracker.Routing.Application.Internal.QueryServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Routing.Interfaces;

/// <summary>経路設計依頼と航海スケジュール検索（US07）。</summary>
[Authorize(Roles = Roles.RouteDesigner)]
public sealed class RoutingController(
    RoutingRequestQueryService requestQueryService,
    IBookingLookup bookingLookup,
    SearchVoyagesQueryService searchVoyagesQueryService) : Controller
{
    [HttpGet("/routing/requests")]
    public async Task<IActionResult> Requests(CancellationToken ct)
        => View(await requestQueryService.FindRouteProposedAsync(ct));

    [HttpGet("/routing/requests/{bookingId}")]
    public async Task<IActionResult> ShowRequest(string bookingId, CancellationToken ct)
    {
        var booking = await bookingLookup.FindByBookingIdAsync(bookingId, ct);
        if (booking is null)
        {
            return NotFound();
        }

        return View("Request", new RoutingRequestViewModel(booking, BuildDefaultForm(booking)));
    }

    [HttpGet("/routing/requests/{bookingId}/voyages")]
    public async Task<IActionResult> SearchVoyages(string bookingId, RoutingSearchForm form, CancellationToken ct)
    {
        if (!Enum.IsDefined(form.CargoType))
        {
            ModelState.AddModelError(nameof(form.CargoType), "貨物種別が不正です。");
        }
        if (form.DepartureFrom > form.DepartureTo)
        {
            ModelState.AddModelError(nameof(form.DepartureTo), "出発期間の終了日は開始日以降で指定してください。");
        }

        if (!ModelState.IsValid)
        {
            return PartialView("_VoyageSearchResults", new VoyageSearchResultsViewModel(form, []));
        }

        var results = await searchVoyagesQueryService.SearchAsync(new SearchVoyagesQuery(
            form.OriginUnlocode,
            form.DestinationUnlocode,
            form.DepartureFrom,
            form.DepartureTo,
            form.CargoType), ct);
        return PartialView("_VoyageSearchResults", new VoyageSearchResultsViewModel(form, results));
    }

    private static RoutingSearchForm BuildDefaultForm(RoutingBookingInfo booking)
    {
        var arrivalDeadline = booking.ArrivalDeadline.ToDateTime(TimeOnly.MinValue);
        var departureTo = new DateTimeOffset(arrivalDeadline, TimeSpan.Zero);
        return new RoutingSearchForm
        {
            OriginUnlocode = booking.OriginUnlocode,
            DestinationUnlocode = booking.DestinationUnlocode,
            DepartureFrom = DateTimeOffset.UtcNow.Date,
            DepartureTo = departureTo,
            CargoType = ParseCargoType(booking.CargoType),
        };
    }

    private static SupportedCargoType ParseCargoType(string cargoType)
        => Enum.TryParse<SupportedCargoType>(cargoType, ignoreCase: true, out var parsed)
            ? parsed
            : SupportedCargoType.General;
}
