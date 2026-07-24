// Package infrastructure は Booking Context の出力アダプター（Repository・ACL）を提供する。
package infrastructure

import (
	"context"
	"math/big"

	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/infrastructure/sqlcgen"
)

// CargoRepository は sqlc + pgx による貨物予約リポジトリ実装。
type CargoRepository struct {
	q *sqlcgen.Queries
}

// NewCargoRepository は CargoRepository を生成する。
func NewCargoRepository(pool *pgxpool.Pool) *CargoRepository {
	return &CargoRepository{q: sqlcgen.New(pool)}
}

// Save は貨物予約を永続化する。
func (r *CargoRepository) Save(ctx context.Context, cargo *domain.Cargo) error {
	spec := cargo.RouteSpec()
	deadline := pgtype.Date{Time: spec.ArrivalDeadline(), Valid: !spec.ArrivalDeadline().IsZero()}

	_, err := r.q.InsertCargo(ctx, sqlcgen.InsertCargoParams{
		BookingID:               cargo.BookingID().Value(),
		ShipperCode:             cargo.ShipperID().Value(),
		BookingStatus:           string(cargo.Status()),
		CargoType:               string(cargo.CargoType()),
		WeightKg:                numericFromFloat3(cargo.Weight().Kg()),
		SpecOriginUnlocode:      spec.Origin().UnLocode(),
		SpecDestinationUnlocode: spec.Destination().UnLocode(),
		SpecArrivalDeadline:     deadline,
		BookingAmountValue:      cargo.BookingAmount().Amount(),
		BookingAmountCurrency:   cargo.BookingAmount().Currency(),
	})
	return err
}

// numericFromFloat3 は重量（小数第 3 位）を pgtype.Numeric に変換する。
func numericFromFloat3(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	n.Int = big.NewInt(int64(f*1000 + 0.5))
	n.Exp = -3
	n.Valid = true
	return n
}
