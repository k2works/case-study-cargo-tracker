// Package infrastructure は Handling Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"time"

	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/infrastructure/sqlcgen"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// HandlingActivityRepository は sqlc + pgx による荷役作業リポジトリ実装。
type HandlingActivityRepository struct {
	q *sqlcgen.Queries
}

// NewHandlingActivityRepository は HandlingActivityRepository を生成する。
func NewHandlingActivityRepository(pool *pgxpool.Pool) *HandlingActivityRepository {
	return &HandlingActivityRepository{q: sqlcgen.New(pool)}
}

// Save は荷役作業を永続化する。
func (r *HandlingActivityRepository) Save(ctx context.Context, a domain.HandlingActivity) error {
	_, err := r.q.InsertHandlingActivity(ctx, sqlcgen.InsertHandlingActivityParams{
		BookingID:             a.CargoBookingId(),
		EventType:             a.Type().String(),
		EventCompletionTime:   pgtype.Timestamp{Time: a.CompletionTime(), Valid: true},
		LocationUnlocode:      a.Location().UnLocode(),
		VoyageNumber:          text(a.VoyageNumber()),
		ConsigneeConfirmation: text(a.ConsigneeConfirmation()),
		OperatorName:          text(a.OperatorName()),
	})
	return err
}

// ListByBookingID は指定予約の荷役作業一覧を返す。
func (r *HandlingActivityRepository) ListByBookingID(ctx context.Context, bookingID string) ([]domain.HandlingActivity, error) {
	rows, err := r.q.ListHandlingActivitiesByBookingId(ctx, bookingID)
	if err != nil {
		return nil, err
	}
	activities := make([]domain.HandlingActivity, 0, len(rows))
	for _, row := range rows {
		ht, err := domain.ParseHandlingType(row.EventType)
		if err != nil {
			return nil, err
		}
		loc, err := shared.NewLocation(row.LocationUnlocode)
		if err != nil {
			return nil, err
		}
		a, err := domain.NewHandlingActivity(
			row.BookingID, ht, loc, timeOf(row.EventCompletionTime),
			row.VoyageNumber.String, row.ConsigneeConfirmation.String, row.OperatorName.String,
		)
		if err != nil {
			return nil, err
		}
		activities = append(activities, a)
	}
	return activities, nil
}

func text(s string) pgtype.Text {
	return pgtype.Text{String: s, Valid: s != ""}
}

func timeOf(ts pgtype.Timestamp) time.Time {
	if !ts.Valid {
		return time.Time{}
	}
	return ts.Time
}
