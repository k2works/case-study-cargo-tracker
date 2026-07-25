// Package infrastructure は Shipper Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"math/big"

	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/infrastructure/sqlcgen"
)

// ShipperRepository は sqlc + pgx による荷主リポジトリ実装。
// application.ShipperRepository ポートを満たす。
type ShipperRepository struct {
	q *sqlcgen.Queries
}

// NewShipperRepository は ShipperRepository を生成する。
func NewShipperRepository(pool *pgxpool.Pool) *ShipperRepository {
	return &ShipperRepository{q: sqlcgen.New(pool)}
}

// Save は荷主を永続化する。
func (r *ShipperRepository) Save(ctx context.Context, shipper *domain.Shipper) error {
	params := sqlcgen.InsertShipperParams{
		ShipperCode: shipper.Code().Value(),
		ShipperType: string(shipper.ShipperType()),
		Name:        shipper.Name().Value(),
		Email:       shipper.Email().Value(),
	}
	if p := shipper.Phone(); p != nil {
		params.Phone = pgtype.Text{String: p.Value(), Valid: true}
	}
	if a := shipper.Address(); a != nil {
		params.Address = pgtype.Text{String: a.Value(), Valid: true}
	}
	if c := shipper.Contract(); c != nil {
		params.ContractNumber = pgtype.Text{String: c.Number().Value(), Valid: true}
		params.DiscountRate = numericFromFloat(c.DiscountRate().Value())
	}

	_, err := r.q.InsertShipper(ctx, params)
	return err
}

// ExistsByEmail は同一メールアドレスの荷主が存在するかを返す。
func (r *ShipperRepository) ExistsByEmail(ctx context.Context, email domain.Email) (bool, error) {
	return r.q.ExistsShipperByEmail(ctx, email.Value())
}

// numericFromFloat は割引率（0.0〜0.30）を pgtype.Numeric に変換する。
func numericFromFloat(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	// NUMERIC(5,4) に合わせ小数第 4 位までを整数 + exp=-4 で表現する。
	n.Int = big.NewInt(int64(f*10000 + 0.5))
	n.Exp = -4
	n.Valid = true
	return n
}
