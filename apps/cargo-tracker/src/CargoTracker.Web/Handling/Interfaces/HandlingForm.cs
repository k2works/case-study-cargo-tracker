using System.ComponentModel.DataAnnotations;
using CargoTracker.Handling.Application.Internal.QueryServices;

namespace CargoTracker.Handling.Interfaces;

public sealed class HandlingForm
{
    [Required(ErrorMessage = "追跡番号は必須です")]
    public string TrackingNumber { get; set; } = string.Empty;

    [Required(ErrorMessage = "作業種別は必須です")]
    public string EventType { get; set; } = "Receive";

    [Required(ErrorMessage = "作業場所は必須です")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    public string LocationUnLocode { get; set; } = string.Empty;

    [Required]
    public DateTimeOffset CompletionTime { get; set; } = DateTimeOffset.UtcNow;

    public string? VoyageNumber { get; set; }

    /// <summary>引取（CLAIM）時の荷受人確認（署名または確認コード）。</summary>
    public string? ConsigneeConfirmation { get; set; }
}

public sealed record HandlingListViewModel(IReadOnlyList<HandlingActivitySummary> Activities);
