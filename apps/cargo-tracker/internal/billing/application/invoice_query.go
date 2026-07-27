package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
)

// InvoiceView は請求書の表示モデル（US21/US22/US23）。
type InvoiceView struct {
	InvoiceNumber   string
	BookingID       string
	ShipperCode     string
	IsCorporate     bool
	BaseAmount      int64
	DiscountRatePct float64
	DiscountAmount  int64
	TaxAmount       int64
	FinalAmount     int64
	Currency        string
	StatusJa        string
	StatusCode      string
	IssuedAt        string
	DueDate         string
	PaidAt          string
	Overdue         bool
}

// InvoiceQueryService は請求書照会ユースケース（読み取り最適化）。
type InvoiceQueryService struct {
	repo InvoiceRepository
}

// NewInvoiceQueryService は InvoiceQueryService を生成する。
func NewInvoiceQueryService(repo InvoiceRepository) *InvoiceQueryService {
	return &InvoiceQueryService{repo: repo}
}

// List は請求書一覧の表示モデルを返す。
func (s *InvoiceQueryService) List(ctx context.Context) ([]InvoiceView, error) {
	invoices, err := s.repo.List(ctx)
	if err != nil {
		return nil, err
	}
	views := make([]InvoiceView, 0, len(invoices))
	for _, inv := range invoices {
		views = append(views, toInvoiceView(inv))
	}
	return views, nil
}

// Find は請求番号から請求書の表示モデルを返す。
func (s *InvoiceQueryService) Find(ctx context.Context, invoiceNumber string) (InvoiceView, error) {
	inv, err := s.repo.FindByInvoiceNumber(ctx, invoiceNumber)
	if err != nil {
		return InvoiceView{}, err
	}
	return toInvoiceView(inv), nil
}

const dateFormat = "2006-01-02"

func toInvoiceView(inv *domain.Invoice) InvoiceView {
	paidAt := ""
	if !inv.PaidAt().IsZero() {
		paidAt = inv.PaidAt().Format(dateFormat)
	}
	return InvoiceView{
		InvoiceNumber:   inv.InvoiceId().Value(),
		BookingID:       inv.BookingId().Value(),
		ShipperCode:     inv.ShipperId().ShipperCode(),
		IsCorporate:     inv.ShipperId().IsCorporate(),
		BaseAmount:      inv.BaseAmount().Amount(),
		DiscountRatePct: inv.DiscountRate().Value() * 100,
		DiscountAmount:  inv.DiscountAmount().Amount(),
		TaxAmount:       inv.TaxAmount().Amount(),
		FinalAmount:     inv.FinalAmount().Amount(),
		Currency:        inv.FinalAmount().Currency(),
		StatusJa:        inv.PaymentStatus().Ja(),
		StatusCode:      string(inv.PaymentStatus()),
		IssuedAt:        inv.IssuedAt().Format(dateFormat),
		DueDate:         inv.DueDate().Format(dateFormat),
		PaidAt:          paidAt,
		Overdue:         inv.PaymentStatus() == domain.PaymentStatusOverdue,
	}
}
