using System.ComponentModel.DataAnnotations;

namespace CargoTracker.Estimation.Interfaces;

/// <summary>見積作成フォーム（US01）。</summary>
public sealed class EstimateForm
{
    [Required(ErrorMessage = "出発地（UN/LOCODE）を入力してください")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    [Display(Name = "出発地（UN/LOCODE）")]
    public string OriginUnLocode { get; set; } = string.Empty;

    [Required(ErrorMessage = "仕向地（UN/LOCODE）を入力してください")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    [Display(Name = "仕向地（UN/LOCODE）")]
    public string DestinationUnLocode { get; set; } = string.Empty;

    [Required(ErrorMessage = "希望期限を入力してください")]
    [DataType(DataType.Date)]
    [Display(Name = "希望期限")]
    public DateOnly ArrivalDeadline { get; set; }

    [Required]
    [Display(Name = "貨物種別")]
    public string CargoType { get; set; } = "General";

    [Range(0.001, 1_000_000, ErrorMessage = "重量は正の値を入力してください")]
    [Display(Name = "重量（kg）")]
    public decimal WeightKg { get; set; }
}
