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
    public async Task<IActionResult> SelectRoute(
        string bookingId, RoutingSearchForm form, string? routeKey, int selectedIndex, CancellationToken ct)
    {
        var booking = await bookingLookup.FindByBookingIdAsync(bookingId, ct);
        if (booking is null)
        {
            return NotFound();
        }

        // 候補は都度算出のため、同一条件で再算出する（算出は決定的）。
        var candidates = await routeCandidateService.FindCandidatesAsync(
            new Location(form.OriginUnlocode),
            new Location(form.DestinationUnlocode),
            booking.ArrivalDeadline,
            form.CargoType,
            ct);

        // 確定対象は候補キー（航海番号列）で照合する。表示と選択の間に候補順が変わっても誤選択しない
        // （IT4 レビュー H2 の是正。インデックス依存の排除）。routeKey 未指定の場合のみインデックスにフォールバックする。
        CandidateRoute? selected;
        if (!string.IsNullOrWhiteSpace(routeKey))
        {
            selected = candidates.FirstOrDefault(c => RouteKey(c) == routeKey);
        }
        else
        {
            selected = selectedIndex >= 0 && selectedIndex < candidates.Count ? candidates[selectedIndex] : null;
        }

        if (selected is null)
        {
            return BadRequest("選択された経路候補が見つかりません。条件を再算出して選び直してください。");
        }

        await selectRouteCommandService.HandleAsync(new SelectRouteCommand(bookingId, selected), ct);
        return LocalRedirect($"/routing/requests/{bookingId}");
    }

    /// <summary>経路候補を一意に識別するキー（航海番号列）。表示順に依存せず確定対象を照合する。</summary>
    private static string RouteKey(CandidateRoute candidate)
        => string.Join("-", candidate.VoyageNumbers.Select(v => v.Value));

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
