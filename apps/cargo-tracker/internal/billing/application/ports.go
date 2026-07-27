// Package application は Billing Context のユースケースとポートを提供する。
package application

import (
	"context"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/domain"
)

// InvoiceRepository は請求書集約の永続化ポート（出力ポート）。
type InvoiceRepository interface {
	Save(ctx context.Context, invoice *domain.Invoice) error
	FindByInvoiceNumber(ctx context.Context, invoiceNumber string) (*domain.Invoice, error)
	ExistsByBookingID(ctx context.Context, bookingID string) (bool, error)
	List(ctx context.Context) ([]*domain.Invoice, error)
}

// InvoiceNumberIssuer は請求番号（INV-YYYYMMDD-NNNN）を原子採番する出力ポート（ADR-0008）。
type InvoiceNumberIssuer interface {
	Next(ctx context.Context, day time.Time) (string, error)
}

// CargoBillingSnapshot は精算に必要な貨物情報（ACL 経由）。
type CargoBillingSnapshot struct {
	BookingID       string
	ShipperCode     string
	WeightKg        float64
	CargoType       string
	TransportStatus string // CLAIMED であること
	DistanceFactor  int64  // 距離係数（区間数/所要日数ベースのスタブ・注2）
}

// CargoBillingSnapshotProvider は Booking の貨物情報を ACL 経由で取得する出力ポート。
type CargoBillingSnapshotProvider interface {
	FetchBillingSnapshot(ctx context.Context, bookingID string) (CargoBillingSnapshot, error)
}

// ShipperContract は荷主の契約情報（ACL 経由）。
type ShipperContract struct {
	ShipperType  string  // CORPORATE / INDIVIDUAL
	DiscountRate float64 // 契約割引率（0〜0.30）
}

// ShipperContractProvider は Shipper の契約割引率を ACL 経由で取得する出力ポート（US22）。
type ShipperContractProvider interface {
	FetchContract(ctx context.Context, shipperCode string) (ShipperContract, error)
}

// BookingSettler は入金確認後に予約を精算済み（SETTLED）にする ACL ポート（US23）。
type BookingSettler interface {
	MarkSettled(ctx context.Context, bookingID string) error
}

// NotificationPort は精算書・未払い通知を抽象化する出力ポート（US23）。
type NotificationPort interface {
	NotifyShipper(ctx context.Context, shipperCode, summary string) error
	NotifyAccountant(ctx context.Context, invoiceNumber, summary string) error
}
