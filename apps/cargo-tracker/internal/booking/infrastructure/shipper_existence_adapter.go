package infrastructure

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/sqlcgen"
)

// ShipperExistenceAdapter は Shipper Context への ACL アダプター。
// booking.application.ShipperExistenceChecker を満たす。
// BC 独立性のため、業務識別子 shipper_code で荷主の存在を確認する。
type ShipperExistenceAdapter struct {
	q *sqlcgen.Queries
}

// NewShipperExistenceAdapter は ShipperExistenceAdapter を生成する。
func NewShipperExistenceAdapter(pool *pgxpool.Pool) *ShipperExistenceAdapter {
	return &ShipperExistenceAdapter{q: sqlcgen.New(pool)}
}

// Exists は指定荷主コードの荷主が存在するかを返す。
func (a *ShipperExistenceAdapter) Exists(ctx context.Context, shipperCode shared.ShipperCode) (bool, error) {
	return a.q.ExistsShipperByCode(ctx, shipperCode.Value())
}
