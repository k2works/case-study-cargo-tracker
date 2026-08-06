package domain

import (
	"errors"
	"math"
	"strings"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyInvoiceId           = errors.New("invoice id must not be empty")
	ErrDiscountRateOutOfRange   = errors.New("discount rate must be between 0 and 0.30")
	ErrInvalidPaymentTransition = errors.New("invalid payment status transition")
)

// InvoiceId は請求書を一意に識別する値オブジェクト。
type InvoiceId struct{ value string }

// NewInvoiceId はバリデーション付きで InvoiceId を生成する。
func NewInvoiceId(v string) (InvoiceId, error) {
	if strings.TrimSpace(v) == "" {
		return InvoiceId{}, ErrEmptyInvoiceId
	}
	return InvoiceId{value: v}, nil
}

// Value は請求番号の文字列表現を返す。
func (i InvoiceId) Value() string { return i.value }

// BillingBookingId は Booking Context の予約を業務識別子で参照する値オブジェクト（BC 独立性）。
type BillingBookingId struct{ value string }

// NewBillingBookingId は予約参照 ID を生成する。
func NewBillingBookingId(v string) BillingBookingId { return BillingBookingId{value: v} }

// Value は予約 ID の文字列表現を返す。
func (b BillingBookingId) Value() string { return b.value }

// BillingShipperId は Shipper Context の荷主を業務識別子で参照し、法人判定を内包する値オブジェクト。
type BillingShipperId struct {
	shipperCode string
	shipperType string
}

// NewBillingShipperId は荷主参照 ID を生成する。
func NewBillingShipperId(shipperCode, shipperType string) BillingShipperId {
	return BillingShipperId{shipperCode: shipperCode, shipperType: shipperType}
}

// ShipperCode は荷主参照コードを返す。
func (s BillingShipperId) ShipperCode() string { return s.shipperCode }

// IsCorporate は法人荷主かを返す。
func (s BillingShipperId) IsCorporate() bool { return s.shipperType == "CORPORATE" }

// DiscountRate は割引率（0〜30%）を表す値オブジェクト。
type DiscountRate struct{ rate float64 }

// NewDiscountRate はバリデーション付きで割引率を生成する。
func NewDiscountRate(rate float64) (DiscountRate, error) {
	if rate < 0 || rate > 0.30 {
		return DiscountRate{}, ErrDiscountRateOutOfRange
	}
	return DiscountRate{rate: rate}, nil
}

// Value は割引率を返す。
func (d DiscountRate) Value() float64 { return d.rate }

// PaymentStatus は支払い状態を表す列挙型。
type PaymentStatus string

const (
	// PaymentStatusPending は未払い。
	PaymentStatusPending PaymentStatus = "PENDING"
	// PaymentStatusConfirmed は支払い確認済み（精算済み）。
	PaymentStatusConfirmed PaymentStatus = "CONFIRMED"
	// PaymentStatusOverdue は支払期限超過。
	PaymentStatusOverdue PaymentStatus = "OVERDUE"
	// PaymentStatusRefunded は返金済み。
	PaymentStatusRefunded PaymentStatus = "REFUNDED"
)

// Ja は支払い状態の日本語表示を返す。
func (s PaymentStatus) Ja() string {
	switch s {
	case PaymentStatusPending:
		return "未払い"
	case PaymentStatusConfirmed:
		return "精算済み"
	case PaymentStatusOverdue:
		return "期限超過"
	case PaymentStatusRefunded:
		return "返金済み"
	default:
		return string(s)
	}
}

// cargoTypeCoefficient は貨物種別の料金係数を返す（GENERAL 1.0 / HAZARDOUS 1.8 / REFRIGERATED 1.5）。
func cargoTypeCoefficient(t shared.CargoType) float64 {
	switch t {
	case shared.CargoTypeHazardous:
		return 1.8
	case shared.CargoTypeRefrigerated:
		return 1.5
	default:
		return 1.0
	}
}

// CalculateBaseAmount は基本料金を算出する。
//
//	基本料金 = 距離係数 × 重量（kg） × 貨物種別係数
//
// 距離係数（distanceFactor・円/kg 相当）は経路の実距離データが無いためスタブ値（区間数/所要日数
// ベースの簡易モデル）を合成ルート/サービスから渡す（IT8 注2）。
func CalculateBaseAmount(distanceFactor int64, weightKg float64, cargoType shared.CargoType) Money {
	amount := math.Round(float64(distanceFactor) * weightKg * cargoTypeCoefficient(cargoType))
	return NewMoney(int64(amount), "JPY")
}

// Invoice は精算書（請求書）を表す集約ルート。
type Invoice struct {
	invoiceId      InvoiceId
	bookingId      BillingBookingId
	shipperId      BillingShipperId
	baseAmount     Money
	discountRate   DiscountRate
	discountAmount Money
	taxAmount      Money
	finalAmount    Money
	paymentStatus  PaymentStatus
	issuedAt       time.Time
	dueDate        time.Time
	paidAt         time.Time
}

// GenerateInvoiceParams は請求書発行のパラメータ集合。
type GenerateInvoiceParams struct {
	InvoiceId    InvoiceId
	BookingId    BillingBookingId
	ShipperId    BillingShipperId
	BaseAmount   Money
	DiscountRate DiscountRate
	TaxRate      float64
	IssuedAt     time.Time
	DueDays      int
}

// GenerateInvoice は基本料金・割引率・税率から請求書を発行する（PENDING 状態・US21/US22）。
//
//	割引額 = 基本料金 × 割引率
//	割引後 = 基本料金 - 割引額
//	消費税 = 割引後 × 税率
//	合計   = 割引後 + 消費税
func GenerateInvoice(p GenerateInvoiceParams) (*Invoice, error) {
	discountAmount := p.BaseAmount.MultiplyRate(p.DiscountRate.Value())
	discounted, err := p.BaseAmount.Subtract(discountAmount)
	if err != nil {
		return nil, err
	}
	taxAmount := discounted.MultiplyRate(p.TaxRate)
	finalAmount, err := discounted.Add(taxAmount)
	if err != nil {
		return nil, err
	}
	return &Invoice{
		invoiceId:      p.InvoiceId,
		bookingId:      p.BookingId,
		shipperId:      p.ShipperId,
		baseAmount:     p.BaseAmount,
		discountRate:   p.DiscountRate,
		discountAmount: discountAmount,
		taxAmount:      taxAmount,
		finalAmount:    finalAmount,
		paymentStatus:  PaymentStatusPending,
		issuedAt:       p.IssuedAt,
		dueDate:        p.IssuedAt.AddDate(0, 0, p.DueDays),
	}, nil
}

// ConfirmPayment は入金を確認し精算済み（CONFIRMED）に遷移する（US23）。
func (i *Invoice) ConfirmPayment(paidAt time.Time) error {
	if i.paymentStatus != PaymentStatusPending && i.paymentStatus != PaymentStatusOverdue {
		return ErrInvalidPaymentTransition
	}
	i.paymentStatus = PaymentStatusConfirmed
	i.paidAt = paidAt
	return nil
}

// MarkOverdue は支払期限を超過した未払い請求を OVERDUE に遷移する（US23）。
// 支払期限は DATE 単位のため、時刻付きの now と日付単位で比較する
// （期限当日に時刻付きで判定しても OVERDUE にしない）。
func (i *Invoice) MarkOverdue(now time.Time) error {
	if i.paymentStatus != PaymentStatusPending {
		return nil
	}
	if dateOnly(now).After(dateOnly(i.dueDate)) {
		i.paymentStatus = PaymentStatusOverdue
	}
	return nil
}

// dateOnly は時刻を切り捨て日付（同一ロケーションの 00:00）に正規化する。
func dateOnly(t time.Time) time.Time {
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, t.Location())
}

// ReconstructInvoice は永続化データから請求書を再構築する。
func ReconstructInvoice(p GenerateInvoiceParams, discountAmount, taxAmount, finalAmount Money, status PaymentStatus, paidAt time.Time) *Invoice {
	return &Invoice{
		invoiceId:      p.InvoiceId,
		bookingId:      p.BookingId,
		shipperId:      p.ShipperId,
		baseAmount:     p.BaseAmount,
		discountRate:   p.DiscountRate,
		discountAmount: discountAmount,
		taxAmount:      taxAmount,
		finalAmount:    finalAmount,
		paymentStatus:  status,
		issuedAt:       p.IssuedAt,
		dueDate:        p.IssuedAt.AddDate(0, 0, p.DueDays),
		paidAt:         paidAt,
	}
}

// アクセサ。
func (i *Invoice) InvoiceId() InvoiceId         { return i.invoiceId }
func (i *Invoice) BookingId() BillingBookingId  { return i.bookingId }
func (i *Invoice) ShipperId() BillingShipperId  { return i.shipperId }
func (i *Invoice) BaseAmount() Money            { return i.baseAmount }
func (i *Invoice) DiscountRate() DiscountRate   { return i.discountRate }
func (i *Invoice) DiscountAmount() Money        { return i.discountAmount }
func (i *Invoice) TaxAmount() Money             { return i.taxAmount }
func (i *Invoice) FinalAmount() Money           { return i.finalAmount }
func (i *Invoice) PaymentStatus() PaymentStatus { return i.paymentStatus }
func (i *Invoice) IssuedAt() time.Time          { return i.issuedAt }
func (i *Invoice) DueDate() time.Time           { return i.dueDate }
func (i *Invoice) PaidAt() time.Time            { return i.paidAt }
