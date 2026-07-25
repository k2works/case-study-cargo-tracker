// Package infrastructure は Booking Context の出力アダプター（Repository・ACL）を提供する。
package infrastructure

import (
	"context"
	"errors"
	"math"
	"math/big"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
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

// Save は貨物予約を永続化する（特殊貨物情報を含む）。
func (r *CargoRepository) Save(ctx context.Context, cargo *domain.Cargo) error {
	spec := cargo.RouteSpec()
	deadline := pgtype.Date{Time: spec.ArrivalDeadline(), Valid: !spec.ArrivalDeadline().IsZero()}

	params := sqlcgen.InsertCargoParams{
		BookingID:               cargo.BookingID().Value(),
		ShipperCode:             cargo.ShipperCode().Value(),
		BookingStatus:           string(cargo.Status()),
		CargoType:               string(cargo.CargoType()),
		WeightKg:                numericFromFloat3(cargo.Weight().Kg()),
		SpecOriginUnlocode:      spec.Origin().UnLocode(),
		SpecDestinationUnlocode: spec.Destination().UnLocode(),
		SpecArrivalDeadline:     deadline,
		BookingAmountValue:      cargo.BookingAmount().Amount(),
		BookingAmountCurrency:   cargo.BookingAmount().Currency(),
	}
	applyHazardous(&params, cargo.HazardousDeclaration())
	applyTemperature(&params, cargo.TemperatureRequirement())

	_, err := r.q.InsertCargo(ctx, params)
	return err
}

// UpdateStatus は予約状態のみを更新する（US13 の状態遷移）。
func (r *CargoRepository) UpdateStatus(ctx context.Context, cargo *domain.Cargo) error {
	rows, err := r.q.UpdateCargoStatus(ctx, sqlcgen.UpdateCargoStatusParams{
		BookingID:     cargo.BookingID().Value(),
		BookingStatus: string(cargo.Status()),
	})
	if err != nil {
		return err
	}
	if rows == 0 {
		return domain.ErrCargoNotFound
	}
	return nil
}

// FindByBookingID は予約番号で貨物予約を復元する。存在しなければ ErrCargoNotFound。
func (r *CargoRepository) FindByBookingID(ctx context.Context, bookingID domain.BookingId) (*domain.Cargo, error) {
	row, err := r.q.GetCargoByBookingId(ctx, bookingID.Value())
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, domain.ErrCargoNotFound
		}
		return nil, err
	}
	return toCargo(row)
}

// applyHazardous は危険物申告を InsertCargoParams に反映する。
func applyHazardous(p *sqlcgen.InsertCargoParams, h *domain.HazardousDeclaration) {
	if h == nil {
		return
	}
	p.HazardousClass = pgtype.Text{String: h.HazardClass(), Valid: true}
	p.UnNumber = pgtype.Text{String: h.UNNumber(), Valid: true}
	p.ProperShippingName = pgtype.Text{String: h.ProperShippingName(), Valid: true}
}

// applyTemperature は温度管理条件を InsertCargoParams に反映する。
func applyTemperature(p *sqlcgen.InsertCargoParams, t *domain.TemperatureRequirement) {
	if t == nil {
		return
	}
	p.MinTemperature = numericFromFloat3(t.MinTemperature())
	p.MaxTemperature = numericFromFloat3(t.MaxTemperature())
	p.TemperatureUnit = pgtype.Text{String: string(t.Unit()), Valid: true}
}

// toCargo は sqlc 行をドメイン集約へ復元する。
func toCargo(row sqlcgen.GetCargoByBookingIdRow) (*domain.Cargo, error) {
	shipperCode, err := shared.NewShipperCode(row.ShipperCode)
	if err != nil {
		return nil, err
	}
	bookingID, err := domain.NewBookingId(row.BookingID)
	if err != nil {
		return nil, err
	}
	origin, err := shared.NewLocation(row.SpecOriginUnlocode)
	if err != nil {
		return nil, err
	}
	dest, err := shared.NewLocation(row.SpecDestinationUnlocode)
	if err != nil {
		return nil, err
	}
	spec, err := domain.NewRouteSpecification(origin, dest, row.SpecArrivalDeadline.Time)
	if err != nil {
		return nil, err
	}
	weight, err := domain.NewWeight(floatFromNumeric(row.WeightKg))
	if err != nil {
		return nil, err
	}
	haz, temp, err := restoreSpecialCargo(row)
	if err != nil {
		return nil, err
	}
	money := domain.NewMoney(row.BookingAmountValue, row.BookingAmountCurrency)
	return domain.Restore(domain.CargoParams{
		BookingID:     bookingID,
		ShipperCode:   shipperCode,
		RouteSpec:     spec,
		CargoType:     domain.CargoType(row.CargoType),
		Weight:        weight,
		BookingAmount: money,
		Hazardous:     haz,
		Temperature:   temp,
	}, domain.BookingStatus(row.BookingStatus)), nil
}

// restoreSpecialCargo は行の nullable 列から特殊貨物情報を復元する。
func restoreSpecialCargo(row sqlcgen.GetCargoByBookingIdRow) (*domain.HazardousDeclaration, *domain.TemperatureRequirement, error) {
	var haz *domain.HazardousDeclaration
	if row.HazardousClass.Valid {
		h, err := domain.NewHazardousDeclaration(row.HazardousClass.String, row.UnNumber.String, row.ProperShippingName.String)
		if err != nil {
			return nil, nil, err
		}
		haz = &h
	}
	var temp *domain.TemperatureRequirement
	if row.TemperatureUnit.Valid {
		unit, err := domain.ParseTemperatureUnit(row.TemperatureUnit.String)
		if err != nil {
			return nil, nil, err
		}
		t, err := domain.NewTemperatureRequirement(floatFromNumeric(row.MinTemperature), floatFromNumeric(row.MaxTemperature), unit)
		if err != nil {
			return nil, nil, err
		}
		temp = &t
	}
	return haz, temp, nil
}

// numericFromFloat3 は小数第 3 位までの値を pgtype.Numeric に変換する。
// 負数も正しく丸めるため math.Round を用いる。
func numericFromFloat3(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	n.Int = big.NewInt(int64(math.Round(f * 1000)))
	n.Exp = -3
	n.Valid = true
	return n
}

// floatFromNumeric は pgtype.Numeric を float64 に変換する（無効値は 0）。
func floatFromNumeric(n pgtype.Numeric) float64 {
	if !n.Valid || n.Int == nil {
		return 0
	}
	f := new(big.Float).SetInt(n.Int)
	f.Mul(f, big.NewFloat(pow10(n.Exp)))
	v, _ := f.Float64()
	return v
}

// pow10 は 10 の整数乗を返す（Exp は通常 -3〜0）。
func pow10(exp int32) float64 {
	result := 1.0
	if exp >= 0 {
		for i := int32(0); i < exp; i++ {
			result *= 10
		}
		return result
	}
	for i := int32(0); i < -exp; i++ {
		result /= 10
	}
	return result
}
