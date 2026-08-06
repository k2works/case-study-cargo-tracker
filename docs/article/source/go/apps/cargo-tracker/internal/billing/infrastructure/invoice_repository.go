// Package infrastructure は Billing Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"errors"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	billingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/infrastructure/sqlcgen"
)

// InvoiceRepository は sqlc + pgx による請求書リポジトリ実装。
type InvoiceRepository struct {
	q *sqlcgen.Queries
}

// NewInvoiceRepository は InvoiceRepository を生成する。
func NewInvoiceRepository(pool *pgxpool.Pool) *InvoiceRepository {
	return &InvoiceRepository{q: sqlcgen.New(pool)}
}

// NextInvoiceNumber は指定日の連番から請求番号（INV-YYYYMMDD-NNNN）を原子採番する（ADR-0008）。
func (r *InvoiceRepository) NextInvoiceNumber(ctx context.Context, day time.Time) (string, error) {
	seq, err := r.q.NextInvoiceSequence(ctx, sqlcgen.NextInvoiceSequenceParams{
		Name: "invoice_number",
		Day:  pgtype.Date{Time: day, Valid: true},
	})
	if err != nil {
		return "", err
	}
	return "INV-" + day.Format("20060102") + "-" + pad4(int(seq)), nil
}

// Save は請求書を永続化する（invoice_number の重複は状態/入金日を更新）。
func (r *InvoiceRepository) Save(ctx context.Context, inv *domain.Invoice) error {
	return r.q.InsertInvoice(ctx, sqlcgen.InsertInvoiceParams{
		InvoiceNumber:       inv.InvoiceId().Value(),
		BookingID:           inv.BookingId().Value(),
		ShipperCode:         inv.ShipperId().ShipperCode(),
		ShipperType:         shipperType(inv.ShipperId().IsCorporate()),
		BaseAmountValue:     toInt32(inv.BaseAmount().Amount()),
		DiscountRate:        numericFromFloat(inv.DiscountRate().Value()),
		DiscountAmountValue: toInt32(inv.DiscountAmount().Amount()),
		TaxAmountValue:      toInt32(inv.TaxAmount().Amount()),
		TotalAmountValue:    toInt32(inv.FinalAmount().Amount()),
		Currency:            inv.FinalAmount().Currency(),
		PaymentStatus:       string(inv.PaymentStatus()),
		IssuedAt:            pgtype.Timestamptz{Time: inv.IssuedAt(), Valid: true},
		DueDate:             pgtype.Date{Time: inv.DueDate(), Valid: true},
		PaidAt:              timestamptz(inv.PaidAt()),
	})
}

// FindByInvoiceNumber は請求番号で請求書を復元する。
func (r *InvoiceRepository) FindByInvoiceNumber(ctx context.Context, invoiceNumber string) (*domain.Invoice, error) {
	row, err := r.q.GetInvoiceByNumber(ctx, invoiceNumber)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, billingapp.ErrInvoiceNotFound
		}
		return nil, err
	}
	return reconstruct(row)
}

// ExistsByBookingID は指定予約の請求書が存在するかを返す（二重請求防止）。
func (r *InvoiceRepository) ExistsByBookingID(ctx context.Context, bookingID string) (bool, error) {
	return r.q.ExistsInvoiceByBookingId(ctx, bookingID)
}

// List は請求書一覧を発行日降順で返す。
func (r *InvoiceRepository) List(ctx context.Context) ([]*domain.Invoice, error) {
	rows, err := r.q.ListInvoices(ctx)
	if err != nil {
		return nil, err
	}
	invoices := make([]*domain.Invoice, 0, len(rows))
	for _, row := range rows {
		inv, err := reconstruct(sqlcgen.GetInvoiceByNumberRow(row))
		if err != nil {
			return nil, err
		}
		invoices = append(invoices, inv)
	}
	return invoices, nil
}

func reconstruct(row sqlcgen.GetInvoiceByNumberRow) (*domain.Invoice, error) {
	id, err := domain.NewInvoiceId(row.InvoiceNumber)
	if err != nil {
		return nil, err
	}
	rate, err := domain.NewDiscountRate(numericToFloat(row.DiscountRate))
	if err != nil {
		return nil, err
	}
	cur := row.Currency
	params := domain.GenerateInvoiceParams{
		InvoiceId:    id,
		BookingId:    domain.NewBillingBookingId(row.BookingID),
		ShipperId:    domain.NewBillingShipperId(row.ShipperCode, row.ShipperType),
		BaseAmount:   domain.NewMoney(int64(row.BaseAmountValue), cur),
		DiscountRate: rate,
		TaxRate:      0.10,
		IssuedAt:     row.IssuedAt.Time,
		DueDays:      30,
	}
	var paidAt time.Time
	if row.PaidAt.Valid {
		paidAt = row.PaidAt.Time
	}
	return domain.ReconstructInvoice(params,
		domain.NewMoney(int64(row.DiscountAmountValue), cur),
		domain.NewMoney(int64(row.TaxAmountValue), cur),
		domain.NewMoney(int64(row.TotalAmountValue), cur),
		domain.PaymentStatus(row.PaymentStatus), paidAt), nil
}

func shipperType(corporate bool) string {
	if corporate {
		return "CORPORATE"
	}
	return "INDIVIDUAL"
}

func pad4(n int) string {
	s := strconv.Itoa(n)
	for len(s) < 4 {
		s = "0" + s
	}
	return s
}

func toInt32(v int64) int32 {
	if v > 2147483647 {
		return 2147483647
	}
	if v < -2147483648 {
		return -2147483648
	}
	return int32(v)
}

func timestamptz(t time.Time) pgtype.Timestamptz {
	if t.IsZero() {
		return pgtype.Timestamptz{Valid: false}
	}
	return pgtype.Timestamptz{Time: t, Valid: true}
}

func numericFromFloat(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	_ = n.Scan(strconv.FormatFloat(f, 'f', 4, 64))
	return n
}

func numericToFloat(n pgtype.Numeric) float64 {
	if !n.Valid {
		return 0
	}
	v, err := n.Float64Value()
	if err != nil {
		return 0
	}
	return v.Float64
}
