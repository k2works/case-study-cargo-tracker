// Package infrastructure は Tracking Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	trackingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/infrastructure/sqlcgen"
)

// TrackingActivityRepository は sqlc + pgx による追跡レコードリポジトリ実装。
type TrackingActivityRepository struct {
	pool *pgxpool.Pool
	q    *sqlcgen.Queries
}

// NewTrackingActivityRepository は TrackingActivityRepository を生成する。
func NewTrackingActivityRepository(pool *pgxpool.Pool) *TrackingActivityRepository {
	return &TrackingActivityRepository{pool: pool, q: sqlcgen.New(pool)}
}

// Save は追跡レコードと新規イベントをトランザクションで永続化する。
// 既存レコードは transport_status を更新し、未永続のイベントのみ追記する。
func (r *TrackingActivityRepository) Save(ctx context.Context, a *domain.TrackingActivity) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()
	q := r.q.WithTx(tx)

	number := a.TrackingNumber().Value()
	status := string(a.CurrentStatus())

	var trackingID int64
	existing, err := q.GetTrackingActivityByNumber(ctx, number)
	switch {
	case err == nil:
		trackingID = existing.ID
		if err := q.UpdateTrackingTransportStatus(ctx, sqlcgen.UpdateTrackingTransportStatusParams{
			TrackingNumber: number, TransportStatus: status,
		}); err != nil {
			return err
		}
	case errors.Is(err, pgx.ErrNoRows):
		trackingID, err = q.InsertTrackingActivity(ctx, sqlcgen.InsertTrackingActivityParams{
			TrackingNumber: number, BookingID: a.BookingId(), TransportStatus: status,
		})
		if err != nil {
			return err
		}
	default:
		return err
	}

	if err := r.saveEvents(ctx, q, trackingID, a.Events()); err != nil {
		return err
	}
	if err := r.saveExceptions(ctx, q, trackingID, a.Exceptions()); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

// saveEvents は未永続の追跡イベントのみを seq 継続で追記する。
func (r *TrackingActivityRepository) saveEvents(ctx context.Context, q *sqlcgen.Queries, trackingID int64, events []domain.TrackingActivityEvent) error {
	persisted, err := q.CountTrackingHandlingEvents(ctx, trackingID)
	if err != nil {
		return err
	}
	for i := int(persisted); i < len(events); i++ {
		e := events[i]
		if err := q.InsertTrackingHandlingEvent(ctx, sqlcgen.InsertTrackingHandlingEventParams{
			TrackingID:       trackingID,
			EventType:        e.EventType(),
			TransportStatus:  string(e.TransportStatus()),
			EventTime:        pgtype.Timestamp{Time: e.CompletionTime(), Valid: true},
			LocationUnlocode: e.Location().UnLocode(),
			VoyageNumber:     pgtype.Text{String: e.VoyageNumber(), Valid: e.VoyageNumber() != ""},
			SeqNumber:        seqNo(i + 1),
		}); err != nil {
			return err
		}
	}
	return nil
}

// saveExceptions は未永続（id=0）を INSERT、解決済み（id!=0）を UPDATE する。
func (r *TrackingActivityRepository) saveExceptions(ctx context.Context, q *sqlcgen.Queries, trackingID int64, exceptions []domain.TrackingExceptionEvent) error {
	for _, ex := range exceptions {
		if ex.ID() == 0 {
			if _, err := q.InsertTrackingExceptionEvent(ctx, sqlcgen.InsertTrackingExceptionEventParams{
				TrackingID:       trackingID,
				ExceptionType:    string(ex.ExceptionType()),
				OccurredAt:       pgtype.Timestamptz{Time: ex.OccurredAt(), Valid: true},
				EscalationFlag:   ex.EscalationFlag(),
				Description:      pgtype.Text{String: ex.Description(), Valid: ex.Description() != ""},
				LocationUnlocode: pgtype.Text{String: ex.Location().UnLocode(), Valid: !ex.Location().IsZero()},
			}); err != nil {
				return err
			}
			continue
		}
		if ex.IsResolved() {
			if err := q.ResolveTrackingExceptionEvent(ctx, sqlcgen.ResolveTrackingExceptionEventParams{
				ID:              ex.ID(),
				ResolvedAt:      pgtype.Timestamptz{Time: ex.ResolvedAt(), Valid: true},
				ResolutionNotes: pgtype.Text{String: ex.ResolutionNotes(), Valid: ex.ResolutionNotes() != ""},
			}); err != nil {
				return err
			}
		}
	}
	return nil
}

// NextTrackingNumber は指定日の連番から次の追跡番号（TRK-YYYYMMDD-NNNN）を採番する。
func (r *TrackingActivityRepository) NextTrackingNumber(ctx context.Context, day time.Time) (string, error) {
	pattern := "TRK-" + day.Format("20060102") + "-%"
	count, err := r.q.CountTrackingActivitiesOnDate(ctx, pattern)
	if err != nil {
		return "", err
	}
	return domain.FormatTrackingNumber(day, int(count)+1), nil
}

// FindByTrackingNumber は追跡番号で追跡レコードを復元する。
func (r *TrackingActivityRepository) FindByTrackingNumber(ctx context.Context, trackingNumber string) (*domain.TrackingActivity, error) {
	row, err := r.q.GetTrackingActivityByNumber(ctx, trackingNumber)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, trackingapp.ErrTrackingNotFound
		}
		return nil, err
	}
	return r.reconstruct(ctx, row.ID, row.TrackingNumber, row.BookingID)
}

// FindByBookingID は予約 ID で追跡レコードを復元する。
func (r *TrackingActivityRepository) FindByBookingID(ctx context.Context, bookingID string) (*domain.TrackingActivity, error) {
	row, err := r.q.GetTrackingActivityByBookingId(ctx, bookingID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, trackingapp.ErrTrackingNotFound
		}
		return nil, err
	}
	return r.reconstruct(ctx, row.ID, row.TrackingNumber, row.BookingID)
}

func (r *TrackingActivityRepository) reconstruct(ctx context.Context, id int64, number, bookingID string) (*domain.TrackingActivity, error) {
	tn, err := domain.NewTrackingNumber(number)
	if err != nil {
		return nil, err
	}
	rows, err := r.q.ListTrackingHandlingEvents(ctx, id)
	if err != nil {
		return nil, err
	}
	events := make([]domain.TrackingActivityEvent, 0, len(rows))
	for _, row := range rows {
		loc, err := shared.NewLocation(row.LocationUnlocode)
		if err != nil {
			return nil, err
		}
		events = append(events, domain.NewTrackingActivityEvent(
			row.EventType, loc, row.EventTime.Time, row.VoyageNumber.String,
			shared.TransportStatus(row.TransportStatus),
		))
	}
	exceptions, err := r.loadExceptions(ctx, id)
	if err != nil {
		return nil, err
	}
	activity := domain.ReconstructTrackingActivity(tn, bookingID, events, exceptions)
	return &activity, nil
}

func (r *TrackingActivityRepository) loadExceptions(ctx context.Context, id int64) ([]domain.TrackingExceptionEvent, error) {
	exRows, err := r.q.ListTrackingExceptionEvents(ctx, id)
	if err != nil {
		return nil, err
	}
	exceptions := make([]domain.TrackingExceptionEvent, 0, len(exRows))
	for _, row := range exRows {
		et, err := domain.ParseExceptionType(row.ExceptionType)
		if err != nil {
			return nil, err
		}
		loc, err := optionalLocation(row.LocationUnlocode)
		if err != nil {
			return nil, err
		}
		var resolvedAt time.Time
		if row.ResolvedAt.Valid {
			resolvedAt = row.ResolvedAt.Time
		}
		exceptions = append(exceptions, domain.ReconstructTrackingExceptionEvent(domain.ReconstructExceptionParams{
			ID:              row.ID,
			ExceptionType:   et,
			Location:        loc,
			OccurredAt:      row.OccurredAt.Time,
			Description:     row.Description.String,
			EscalationFlag:  row.EscalationFlag,
			ResolvedAt:      resolvedAt,
			ResolutionNotes: row.ResolutionNotes.String,
		}))
	}
	return exceptions, nil
}

// optionalLocation は NULL 可能な UN/LOCODE を Location へ変換する（空はゼロ値）。
func optionalLocation(v pgtype.Text) (shared.Location, error) {
	if !v.Valid || v.String == "" {
		return shared.Location{}, nil
	}
	return shared.NewLocation(v.String)
}

// seqNo は連番を int32 へ安全に変換する（負値・オーバーフローを抑止）。
func seqNo(n int) int32 {
	if n < 0 {
		return 0
	}
	if n > 2147483647 {
		return 2147483647
	}
	return int32(n)
}
