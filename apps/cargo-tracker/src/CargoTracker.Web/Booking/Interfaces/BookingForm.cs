using System.ComponentModel.DataAnnotations;
using CargoTracker.Booking.Application.Internal.QueryServices;
using CargoTracker.Booking.Domain.Model;

namespace CargoTracker.Booking.Interfaces;

/// <summary>貨物予約登録フォーム（US04）。</summary>
public sealed class BookingForm
{
    [Required(ErrorMessage = "荷主 ID を選択してください")]
    [Display(Name = "荷主 ID")]
    public string ShipperId { get; set; } = string.Empty;

    [Required(ErrorMessage = "出発地（UN/LOCODE）を入力してください")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    [Display(Name = "出発地（UN/LOCODE）")]
    public string OriginUnLocode { get; set; } = string.Empty;

    [Required(ErrorMessage = "仕向地（UN/LOCODE）を入力してください")]
    [StringLength(5, MinimumLength = 5, ErrorMessage = "UN/LOCODE は 5 文字です")]
    [Display(Name = "仕向地（UN/LOCODE）")]
    public string DestinationUnLocode { get; set; } = string.Empty;

    [Required(ErrorMessage = "希望着日を入力してください")]
    [DataType(DataType.Date)]
    [Display(Name = "希望着日")]
    public DateOnly ArrivalDeadline { get; set; }

    [Required]
    [Display(Name = "貨物種別")]
    public string CargoType { get; set; } = "General";

    [Range(0.001, 1_000_000, ErrorMessage = "重量は正の値を入力してください")]
    [Display(Name = "重量（kg）")]
    public decimal Weight { get; set; }

    [Display(Name = "長さ（cm）")]
    public decimal? DimensionLength { get; set; }

    [Display(Name = "幅（cm）")]
    public decimal? DimensionWidth { get; set; }

    [Display(Name = "高さ（cm）")]
    public decimal? DimensionHeight { get; set; }

    [Range(1, int.MaxValue, ErrorMessage = "個数は 1 以上で入力してください")]
    [Display(Name = "個数")]
    public int? Quantity { get; set; }

    [StringLength(500, ErrorMessage = "品名は 500 文字以内で入力してください")]
    [Display(Name = "品名")]
    public string? Description { get; set; }

    [Display(Name = "危険物クラス")]
    public string? HazardousClass { get; set; }

    [Display(Name = "UN 番号")]
    public string? UnNumber { get; set; }

    [Display(Name = "正式輸送品名")]
    public string? ProperShippingName { get; set; }

    [Display(Name = "最低温度")]
    public decimal? MinTemperature { get; set; }

    [Display(Name = "最高温度")]
    public decimal? MaxTemperature { get; set; }

    [Display(Name = "温度単位")]
    public TemperatureUnit? TemperatureUnit { get; set; }

    public IReadOnlyList<ShipperOption> Shippers { get; set; } = [];
}
