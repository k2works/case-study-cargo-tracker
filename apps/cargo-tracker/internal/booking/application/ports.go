// Package application は Booking Context のユースケースとポートを提供する。
package application

import (
	"context"
	"time"

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

// CargoItineraryRepository は確定経路（CargoItinerary）の取得・永続化ポート（US09）。
type CargoItineraryRepository interface {
	FindByBookingID(ctx context.Context, bookingID domain.BookingId) (*domain.Cargo, error)
	SaveItinerary(ctx context.Context, cargo *domain.Cargo) error
}

// RouteSearcher は経路探索への ACL ポート（US08/US09）。
// Booking は Routing に直接依存せず、自 BC の語彙（RouteLegDTO）で経路候補を受け取る。
// 実体は合成ルート（cmd/server）で Routing の SearchRoutesService を変換注入する。
type RouteSearcher interface {
	Search(ctx context.Context, spec RouteSearchSpec) ([]RouteCandidateDTO, error)
}

// RouteSearchSpec は経路探索の要求（Booking の語彙）。
type RouteSearchSpec struct {
	OriginUnLocode      string
	DestinationUnLocode string
	ArrivalDeadline     time.Time
	CargoType           string
}

// RouteLegDTO は経路候補を構成する 1 区間（Booking の語彙）。
type RouteLegDTO struct {
	VoyageNumber   string
	LoadUnLocode   string
	UnloadUnLocode string
	LoadTime       time.Time
	UnloadTime     time.Time
}

// RouteCandidateDTO は経路候補（Booking の語彙）。
type RouteCandidateDTO struct {
	Legs          []RouteLegDTO
	TransitDays   int
	EstimatedCost int64
	Waypoints     []string
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

// NotificationPort は荷主への通知送信を抽象化する出力ポート（US12）。
// 通知は Booking の関心事のため本 BC の application に定義する（shared には置かない・ADR-0005 の ACL 方針）。
// 実体は cmd/server でログ実装を注入する（実メール送信は後続の外部連携）。
type NotificationPort interface {
	Notify(ctx context.Context, shipperCode shared.ShipperCode, summary string) error
}

// NotificationRepository は確定経路通知の送信記録の永続化ポート（US12）。
type NotificationRepository interface {
	Save(ctx context.Context, bookingID domain.BookingId, notification domain.Notification) error
	ListByBookingID(ctx context.Context, bookingID domain.BookingId) ([]domain.Notification, error)
}
