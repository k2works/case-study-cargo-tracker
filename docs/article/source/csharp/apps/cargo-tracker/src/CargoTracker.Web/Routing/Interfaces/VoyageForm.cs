using System.ComponentModel.DataAnnotations;

namespace CargoTracker.Routing.Interfaces;

/// <summary>航海スケジュール登録フォーム（US24）。</summary>
public sealed class VoyageForm
{
    [Required(ErrorMessage = "航海番号を入力してください")]
    [StringLength(20, ErrorMessage = "航海番号は 20 文字以内で入力してください")]
    [Display(Name = "航海番号")]
    public string VoyageNumber { get; set; } = string.Empty;

    public long Version { get; set; }

    [Required(ErrorMessage = "船名を入力してください")]
    [StringLength(200, ErrorMessage = "船名は 200 文字以内で入力してください")]
    [Display(Name = "船名")]
    public string VesselName { get; set; } = string.Empty;

    [Required(ErrorMessage = "運送会社を入力してください")]
    [StringLength(200, ErrorMessage = "運送会社は 200 文字以内で入力してください")]
    [Display(Name = "運送会社")]
    public string Carrier { get; set; } = string.Empty;

    [Display(Name = "対応貨物種別")]
    public List<string> SupportedCargoTypes { get; set; } = [];

    public List<CarrierMovementForm> CarrierMovements { get; set; } = [];
}

public sealed class CarrierMovementForm
{
    [Required(ErrorMessage = "出発港を入力してください")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    public string DepartureLocationUnLocode { get; set; } = string.Empty;

    [Required(ErrorMessage = "到着港を入力してください")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    public string ArrivalLocationUnLocode { get; set; } = string.Empty;

    [Required(ErrorMessage = "出発日時を入力してください")]
    public DateTimeOffset? DepartureDate { get; set; }

    [Required(ErrorMessage = "到着日時を入力してください")]
    public DateTimeOffset? ArrivalDate { get; set; }
}

public sealed record CarrierMovementRowForm(int Index, CarrierMovementForm Movement);

public sealed record EditVoyageViewModel(
    VoyageForm Form,
    CargoTracker.Routing.Application.Internal.QueryServices.VoyageDetail Existing);
