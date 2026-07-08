using System.ComponentModel.DataAnnotations;

namespace CargoTracker.Shipper.Interfaces;

/// <summary>荷主登録フォーム（US02 個人 / US03 法人）。法人選択時は契約番号・割引率が必須。</summary>
public sealed class ShipperForm
{
    [Required]
    [Display(Name = "荷主種別")]
    public string ShipperType { get; set; } = "Individual";

    [Required(ErrorMessage = "氏名／社名を入力してください")]
    [StringLength(200)]
    [Display(Name = "氏名／社名")]
    public string Name { get; set; } = string.Empty;

    [Required(ErrorMessage = "メールアドレスを入力してください")]
    [EmailAddress(ErrorMessage = "メールアドレスの形式が正しくありません")]
    [StringLength(200)]
    [Display(Name = "メールアドレス")]
    public string Email { get; set; } = string.Empty;

    [StringLength(50)]
    [Display(Name = "電話番号")]
    public string? Phone { get; set; }

    [StringLength(50)]
    [Display(Name = "契約番号（法人）")]
    public string? ContractNumber { get; set; }

    [Range(0, 30, ErrorMessage = "割引率は 0〜30% の範囲で入力してください")]
    [Display(Name = "割引率（％・法人）")]
    public decimal? DiscountPercent { get; set; }

    public bool IsCorporate => string.Equals(ShipperType, "Corporate", StringComparison.OrdinalIgnoreCase);
}
