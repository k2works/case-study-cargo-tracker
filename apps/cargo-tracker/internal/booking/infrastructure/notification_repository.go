package infrastructure

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/infrastructure/sqlcgen"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// NotificationRepository は sqlc + pgx による確定経路通知の記録リポジトリ実装（US12）。
type NotificationRepository struct {
	q *sqlcgen.Queries
}

// NewNotificationRepository は NotificationRepository を生成する。
func NewNotificationRepository(pool *pgxpool.Pool) *NotificationRepository {
	return &NotificationRepository{q: sqlcgen.New(pool)}
}

// Save は通知記録を永続化する。cargo_id は booking_id から解決する。
func (r *NotificationRepository) Save(ctx context.Context, bookingID domain.BookingId, n domain.Notification) error {
	cargoID, err := r.q.GetCargoIdByBookingId(ctx, bookingID.Value())
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return domain.ErrCargoNotFound
		}
		return err
	}
	return r.q.InsertNotification(ctx, sqlcgen.InsertNotificationParams{
		CargoID:     cargoID,
		ShipperCode: n.ShipperCode().Value(),
		Summary:     n.Summary(),
		SentAt:      pgtype.Timestamp{Time: n.SentAt(), Valid: true},
	})
}

// ListByBookingID は予約番号で通知記録を新しい順に復元する。
func (r *NotificationRepository) ListByBookingID(ctx context.Context, bookingID domain.BookingId) ([]domain.Notification, error) {
	rows, err := r.q.ListNotificationsByBookingId(ctx, bookingID.Value())
	if err != nil {
		return nil, err
	}
	notifications := make([]domain.Notification, 0, len(rows))
	for _, row := range rows {
		sc, err := shared.NewShipperCode(row.ShipperCode)
		if err != nil {
			return nil, err
		}
		n, err := domain.NewNotification(sc, row.Summary, row.SentAt.Time)
		if err != nil {
			return nil, err
		}
		notifications = append(notifications, n)
	}
	return notifications, nil
}
