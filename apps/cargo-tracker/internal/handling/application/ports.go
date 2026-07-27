// Package application は Handling Context のユースケースとポートを提供する。
package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
)

// HandlingActivityRepository は荷役作業集約の永続化ポート（出力ポート）。
type HandlingActivityRepository interface {
	Save(ctx context.Context, activity domain.HandlingActivity) error
	ListByBookingID(ctx context.Context, bookingID string) ([]domain.HandlingActivity, error)
}

// CargoSnapshotProvider は貨物情報を ACL 経由で取得する出力ポート。
// Handling は Booking に直接依存せず、自 BC の語彙（CargoSnapshot）で貨物情報を受け取る。
// 実体は合成ルート（cmd/server）で Booking のクエリを変換注入する。
type CargoSnapshotProvider interface {
	FetchSnapshot(ctx context.Context, bookingID string) (domain.CargoSnapshot, error)
}

// HandlingEventPublisher は荷役作業登録イベントを発行する出力ポート。
// Tracking / Booking への状態同期は合成ルートでハンドラを配線する（BC 独立性）。
type HandlingEventPublisher interface {
	Publish(ctx context.Context, event HandlingActivityRegisteredEvent) error
}

// HandlingActivityRegisteredEvent は荷役作業が登録されたことを表すドメインイベント。
type HandlingActivityRegisteredEvent struct {
	BookingID        string
	HandlingType     string
	LocationUnLocode string
	VoyageNumber     string
	TransportStatus  string
	Misrouted        bool
}
