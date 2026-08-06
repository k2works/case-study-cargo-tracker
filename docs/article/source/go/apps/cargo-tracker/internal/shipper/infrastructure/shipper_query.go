package infrastructure

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/infrastructure/sqlcgen"
)

// ShipperQuery は sqlc による荷主読み取りアダプター（CQRS クエリ側）。
// application.ShipperQueryPort を満たす。
type ShipperQuery struct {
	q *sqlcgen.Queries
}

// NewShipperQuery は ShipperQuery を生成する。
func NewShipperQuery(pool *pgxpool.Pool) *ShipperQuery {
	return &ShipperQuery{q: sqlcgen.New(pool)}
}

// ListShippers は登録済み荷主の一覧を表示用 DTO で返す。
func (r *ShipperQuery) ListShippers(ctx context.Context) ([]application.ShipperListItem, error) {
	rows, err := r.q.ListShippers(ctx)
	if err != nil {
		return nil, err
	}
	items := make([]application.ShipperListItem, 0, len(rows))
	for _, row := range rows {
		items = append(items, application.ShipperListItem{
			Code:  row.ShipperCode,
			Name:  row.Name,
			Type:  row.ShipperType,
			Email: row.Email,
		})
	}
	return items, nil
}
