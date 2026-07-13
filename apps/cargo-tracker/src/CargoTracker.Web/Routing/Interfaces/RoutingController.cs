using CargoTracker.Routing.Application.Internal.CommandServices;
using CargoTracker.Routing.Application.Internal.OutboundServices;
using CargoTracker.Routing.Application.Internal.QueryServices;
using CargoTracker.Routing.Domain.Model;
using CargoTracker.Routing.Domain.Repositories;
using CargoTracker.Shared.Domain.Model;
using CargoTracker.Shared.Infrastructure.Auth;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace CargoTracker.Routing.Interfaces;

/// <summary>経路設計依頼・航海スケジュール検索・経路選択（US07/US08/US09/US10）。</summary>
[Authorize(Roles = Roles.RouteDesigner)]
public sealed class RoutingController(
    RoutingRequestQueryService requestQueryService,
    IBookingLookup bookingLookup,
    SearchVoyagesQueryService searchVoyagesQueryService,
    IRouteCandidateService routeCandidateService,
    SelectRouteCommandService selectRouteCommandService,
    ISelectedRouteRepository selectedRouteRepository) : Controller
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

        var confirmed = await selectedRouteRepository.FindByBookingIdAsync(bookingId, ct);
        return View("Request", new RoutingRequestViewModel(booking, BuildDefaultForm(booking), confirmed?.Route));
    }

    [HttpPost("/routing/requests/{bookingId}/select")]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> SelectRoute(string bookingId, RoutingSearchForm form, int selectedIndex, CancellationToken ct)
    {
        var booking = await bookingLookup.FindByBookingIdAsync(bookingId, ct);
        if (booking is null)
        {
            return NotFound();
        }

        // 候補は都度算出のため、同一条件で再算出し選択インデックスで確定対象を特定する（算出は決定的）。
        var candidates = await routeCandidateService.FindCandidatesAsync(
            new Location(form.OriginUnlocode),
            new Location(form.DestinationUnlocode),
            booking.ArrivalDeadline,
            form.CargoType,
            ct);

        if (selectedIndex < 0 || selectedIndex >= candidates.Count)
        {
            return BadRequest("選択された経路候補が見つかりません。");
        }

        await selectRouteCommandService.HandleAsync(new SelectRouteCommand(bookingId, candidates[selectedIndex]), ct);
        return LocalRedirect($"/routing/requests/{bookingId}");
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

    [HttpGet("/routing/requests/{bookingId}/candidates")]
    public async Task<IActionResult> Candidates(string bookingId, RoutingSearchForm form, CancellationToken ct)
    {
        var booking = await bookingLookup.FindByBookingIdAsync(bookingId, ct);
        if (booking is null)
        {
            return NotFound();
        }

        var candidates = await routeCandidateService.FindCandidatesAsync(
            new Location(form.OriginUnlocode),
            new Location(form.DestinationUnlocode),
            booking.ArrivalDeadline,
            form.CargoType,
            ct);

        return PartialView("_RouteCandidates", new RouteCandidatesViewModel(bookingId, form, candidates));
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
