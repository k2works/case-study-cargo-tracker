using System.ComponentModel.DataAnnotations;
using CargoTracker.Routing.Application.Internal.OutboundServices;
using CargoTracker.Routing.Application.Internal.QueryServices;
using CargoTracker.Routing.Domain.Model;

namespace CargoTracker.Routing.Interfaces;

public sealed class RoutingSearchForm
{
    [Required]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    public string OriginUnlocode { get; set; } = string.Empty;

    [Required]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    public string DestinationUnlocode { get; set; } = string.Empty;

    [Required]
    public DateTimeOffset DepartureFrom { get; set; }

    [Required]
    public DateTimeOffset DepartureTo { get; set; }

    [Required]
    public SupportedCargoType CargoType { get; set; } = SupportedCargoType.General;
}

public sealed record RoutingRequestViewModel(RoutingBookingInfo Booking, RoutingSearchForm Form);

public sealed record VoyageSearchResultsViewModel(
    RoutingSearchForm Form,
    IReadOnlyList<VoyageSearchResult> Results);

public sealed record RouteCandidatesViewModel(
    RoutingSearchForm Form,
    IReadOnlyList<CargoTracker.Routing.Domain.Model.CandidateRoute> Candidates);
