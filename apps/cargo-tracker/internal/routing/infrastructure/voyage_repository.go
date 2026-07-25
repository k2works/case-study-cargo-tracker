// Package infrastructure は Routing Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"errors"
	"math"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/infrastructure/sqlcgen"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrVoyageNotFound は指定航海が存在しない場合に返される。
var ErrVoyageNotFound = errors.New("voyage not found")

// VoyageRepository は sqlc + pgx による航海リポジトリ実装。
type VoyageRepository struct {
	pool *pgxpool.Pool
	q    *sqlcgen.Queries
}

// NewVoyageRepository は VoyageRepository を生成する。
func NewVoyageRepository(pool *pgxpool.Pool) *VoyageRepository {
	return &VoyageRepository{pool: pool, q: sqlcgen.New(pool)}
}

// Save は航海スケジュールを新規保存する（voyage + carrier_movement をトランザクションで）。
func (r *VoyageRepository) Save(ctx context.Context, voyage *domain.Voyage) error {
	return r.inTx(ctx, func(q *sqlcgen.Queries) error {
		voyageID, err := q.InsertVoyage(ctx, sqlcgen.InsertVoyageParams{
			VoyageNumber:        voyage.VoyageNumber().Value(),
			VesselName:          voyage.VesselName(),
			Carrier:             voyage.Carrier(),
			SupportedCargoTypes: encodeCargoTypes(voyage.SupportedCargoTypes()),
		})
		if err != nil {
			return err
		}
		return insertMovements(ctx, q, voyageID, voyage)
	})
}

// Update は既存航海のスケジュール・属性を上書き更新する（US25）。
func (r *VoyageRepository) Update(ctx context.Context, voyage *domain.Voyage) error {
	return r.inTx(ctx, func(q *sqlcgen.Queries) error {
		if err := q.UpdateVoyage(ctx, sqlcgen.UpdateVoyageParams{
			VoyageNumber:        voyage.VoyageNumber().Value(),
			VesselName:          voyage.VesselName(),
			Carrier:             voyage.Carrier(),
			SupportedCargoTypes: encodeCargoTypes(voyage.SupportedCargoTypes()),
		}); err != nil {
			return err
		}
		row, err := q.GetVoyageByNumber(ctx, voyage.VoyageNumber().Value())
		if err != nil {
			return err
		}
		if err := q.DeleteCarrierMovements(ctx, row.ID); err != nil {
			return err
		}
		return insertMovements(ctx, q, row.ID, voyage)
	})
}

// Exists は指定航海番号の航海が存在するかを返す。
func (r *VoyageRepository) Exists(ctx context.Context, voyageNumber domain.VoyageNumber) (bool, error) {
	return r.q.ExistsVoyageByNumber(ctx, voyageNumber.Value())
}

// FindByNumber は航海番号で航海を復元する。存在しなければ ErrVoyageNotFound。
func (r *VoyageRepository) FindByNumber(ctx context.Context, voyageNumber domain.VoyageNumber) (*domain.Voyage, error) {
	row, err := r.q.GetVoyageByNumber(ctx, voyageNumber.Value())
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrVoyageNotFound
		}
		return nil, err
	}
	movs, err := r.q.ListCarrierMovements(ctx, row.ID)
	if err != nil {
		return nil, err
	}
	return toVoyage(row.VoyageNumber, row.VesselName, row.Carrier, row.SupportedCargoTypes, movs)
}

func (r *VoyageRepository) inTx(ctx context.Context, fn func(*sqlcgen.Queries) error) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()
	if err := fn(r.q.WithTx(tx)); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func insertMovements(ctx context.Context, q *sqlcgen.Queries, voyageID int64, voyage *domain.Voyage) error {
	for _, m := range voyage.Schedule().Movements() {
		if err := q.InsertCarrierMovement(ctx, sqlcgen.InsertCarrierMovementParams{
			VoyageID:                  voyageID,
			DepartureLocationUnlocode: m.Departure().UnLocode(),
			ArrivalLocationUnlocode:   m.Arrival().UnLocode(),
			DepartureDate:             pgtype.Timestamp{Time: m.DepartureTime(), Valid: true},
			ArrivalDate:               pgtype.Timestamp{Time: m.ArrivalTime(), Valid: true},
			SeqNumber:                 seqToInt32(m.Seq()),
		}); err != nil {
			return err
		}
	}
	return nil
}

// toVoyage は sqlc 行から Voyage 集約を復元する。
func toVoyage(voyageNumber, vesselName, carrier, cargoTypesCSV string, movs []sqlcgen.ListCarrierMovementsRow) (*domain.Voyage, error) {
	vn, err := domain.NewVoyageNumber(voyageNumber)
	if err != nil {
		return nil, err
	}
	movements := make([]domain.CarrierMovement, 0, len(movs))
	for _, m := range movs {
		dep, err := shared.NewLocation(m.DepartureLocationUnlocode)
		if err != nil {
			return nil, err
		}
		arr, err := shared.NewLocation(m.ArrivalLocationUnlocode)
		if err != nil {
			return nil, err
		}
		cm, err := domain.NewCarrierMovement(dep, arr, m.DepartureDate.Time, m.ArrivalDate.Time, int(m.SeqNumber))
		if err != nil {
			return nil, err
		}
		movements = append(movements, cm)
	}
	schedule, err := domain.NewSchedule(movements)
	if err != nil {
		return nil, err
	}
	return domain.Restore(vn, vesselName, carrier, schedule, decodeCargoTypes(cargoTypesCSV)), nil
}

// seqToInt32 は区間順序を int32 へ安全に変換する（範囲外は上限/下限にクランプ）。
func seqToInt32(seq int) int32 {
	if seq > math.MaxInt32 {
		return math.MaxInt32
	}
	if seq < math.MinInt32 {
		return math.MinInt32
	}
	return int32(seq)
}

// encodeCargoTypes は対応貨物種別を CSV 文字列へ変換する。
func encodeCargoTypes(cts []shared.CargoType) string {
	parts := make([]string, len(cts))
	for i, c := range cts {
		parts[i] = string(c)
	}
	return strings.Join(parts, ",")
}

// decodeCargoTypes は CSV 文字列を対応貨物種別へ復元する。
func decodeCargoTypes(csv string) []shared.CargoType {
	if strings.TrimSpace(csv) == "" {
		return nil
	}
	parts := strings.Split(csv, ",")
	cts := make([]shared.CargoType, 0, len(parts))
	for _, p := range parts {
		cts = append(cts, shared.CargoType(strings.TrimSpace(p)))
	}
	return cts
}
