package infrastructure

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/sqlcgen"
)

// CargoQuery は sqlc による貨物予約読み取りアダプター（CQRS クエリ側）。
// application.CargoQueryPort を満たす。
type CargoQuery struct {
	q *sqlcgen.Queries
}

// NewCargoQuery は CargoQuery を生成する。
func NewCargoQuery(pool *pgxpool.Pool) *CargoQuery {
	return &CargoQuery{q: sqlcgen.New(pool)}
}

// ListCargos は登録済み貨物予約の一覧を表示用 DTO で返す。
func (r *CargoQuery) ListCargos(ctx context.Context) ([]application.CargoListItem, error) {
	rows, err := r.q.ListCargos(ctx)
	if err != nil {
		return nil, err
	}
	items := make([]application.CargoListItem, 0, len(rows))
	for _, row := range rows {
		items = append(items, application.CargoListItem{
			BookingID:   row.BookingID,
			ShipperCode: row.ShipperCode,
			Origin:      row.SpecOriginUnlocode,
			Destination: row.SpecDestinationUnlocode,
			CargoType:   row.CargoType,
			Status:      row.BookingStatus,
			StatusJa:    domain.BookingStatus(row.BookingStatus).Ja(),
		})
	}
	return items, nil
}
