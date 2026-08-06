package infrastructure

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	billingapp "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/billing/infrastructure/sqlcgen"
)

// ShipperContractAdapter は Shipper Context への ACL アダプター。
// BC 独立性のため業務識別子 shipper_code で荷主種別・契約割引率を直読する（ADR-0005 先例）。
type ShipperContractAdapter struct {
	q *sqlcgen.Queries
}

// NewShipperContractAdapter は ShipperContractAdapter を生成する。
func NewShipperContractAdapter(pool *pgxpool.Pool) *ShipperContractAdapter {
	return &ShipperContractAdapter{q: sqlcgen.New(pool)}
}

// FetchContract は shipper_code から荷主種別・契約割引率を取得する（US22）。
func (a *ShipperContractAdapter) FetchContract(ctx context.Context, shipperCode string) (billingapp.ShipperContract, error) {
	row, err := a.q.GetShipperContract(ctx, shipperCode)
	if err != nil {
		return billingapp.ShipperContract{}, err
	}
	return billingapp.ShipperContract{
		ShipperType:  row.ShipperType,
		DiscountRate: numericToFloat(row.DiscountRate),
	}, nil
}
