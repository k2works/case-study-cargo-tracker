// Package application は Booking Context のユースケースとポートを提供する。
package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// CargoRepository は貨物予約集約の永続化ポート（出力ポート）。
type CargoRepository interface {
	Save(ctx context.Context, cargo *domain.Cargo) error
}

// BookingLifecycleRepository は予約ライフサイクル操作（US13）の永続化ポート。
// 予約の取得と状態更新を担う。
type BookingLifecycleRepository interface {
	FindByBookingID(ctx context.Context, bookingID domain.BookingId) (*domain.Cargo, error)
	UpdateStatus(ctx context.Context, cargo *domain.Cargo) error
}

// ShipperExistenceChecker は Shipper Context への ACL ポート。
// Booking は Shipper に直接依存せず、業務識別子 ShipperCode で荷主の存在を確認する。
type ShipperExistenceChecker interface {
	Exists(ctx context.Context, shipperCode shared.ShipperCode) (bool, error)
}

// IDGenerator は一意 ID（UUID）を採番する横断ポート。
type IDGenerator interface {
	Generate() string
}

// EventPublisher はドメインイベントを発行する出力ポート。
type EventPublisher interface {
	Publish(ctx context.Context, name string, payload any) error
}
