// Package infrastructure は Estimation Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"errors"
	"math"
	"math/big"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/domain"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/estimation/infrastructure/sqlcgen"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrEstimateNotFound は指定見積が存在しない場合に返される。
var ErrEstimateNotFound = errors.New("estimate not found")

// EstimateRepository は sqlc + pgx による見積リポジトリ実装。
type EstimateRepository struct {
	pool *pgxpool.Pool
	q    *sqlcgen.Queries
}

// NewEstimateRepository は EstimateRepository を生成する。
func NewEstimateRepository(pool *pgxpool.Pool) *EstimateRepository {
	return &EstimateRepository{pool: pool, q: sqlcgen.New(pool)}
}

// Save は見積とルート候補をトランザクションで保存する。
func (r *EstimateRepository) Save(ctx context.Context, e *domain.Estimate) error {
	uid, err := parseUUID(e.EstimateId().Value())
	if err != nil {
		return err
	}
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()
	q := r.q.WithTx(tx)

	estimateID, err := q.InsertEstimate(ctx, sqlcgen.InsertEstimateParams{
		EstimateID:          uid,
		OriginUnlocode:      e.Origin().UnLocode(),
		DestinationUnlocode: e.Destination().UnLocode(),
		ArrivalDeadline:     pgtype.Date{Time: e.ArrivalDeadline(), Valid: !e.ArrivalDeadline().IsZero()},
		CargoType:           string(e.CargoType()),
		WeightKg:            numericFromFloat3(e.WeightKg()),
		Status:              string(e.Status()),
	})
	if err != nil {
		return err
	}
	for i, c := range e.Candidates() {
		if err := q.InsertRouteCandidate(ctx, sqlcgen.InsertRouteCandidateParams{
			EstimateID:    estimateID,
			VoyageNumber:  c.VoyageNumber(),
			TransitDays:   safeInt32(c.TransitDays()),
			EstimatedCost: c.EstimatedCost(),
			SeqNumber:     safeInt32(i + 1),
			Waypoints:     strings.Join(c.Waypoints(), ","),
		}); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

// FindByEstimateId は見積 ID で見積を復元する。存在しなければ ErrEstimateNotFound。
func (r *EstimateRepository) FindByEstimateId(ctx context.Context, id domain.EstimateId) (*domain.Estimate, error) {
	uid, err := parseUUID(id.Value())
	if err != nil {
		return nil, err
	}
	row, err := r.q.GetEstimateByEstimateId(ctx, uid)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, ErrEstimateNotFound
		}
		return nil, err
	}
	cands, err := r.q.ListRouteCandidates(ctx, row.ID)
	if err != nil {
		return nil, err
	}
	return toEstimate(row, cands)
}

// ListAll は全見積を集約として復元して返す。
func (r *EstimateRepository) ListAll(ctx context.Context) ([]*domain.Estimate, error) {
	rows, err := r.q.ListEstimates(ctx)
	if err != nil {
		return nil, err
	}
	estimates := make([]*domain.Estimate, 0, len(rows))
	for _, row := range rows {
		cands, err := r.q.ListRouteCandidates(ctx, row.ID)
		if err != nil {
			return nil, err
		}
		e, err := toEstimate(sqlcgen.GetEstimateByEstimateIdRow(row), cands)
		if err != nil {
			return nil, err
		}
		estimates = append(estimates, e)
	}
	return estimates, nil
}

// toEstimate は sqlc 行から Estimate 集約を復元する。
func toEstimate(row sqlcgen.GetEstimateByEstimateIdRow, cands []sqlcgen.ListRouteCandidatesRow) (*domain.Estimate, error) {
	id, err := domain.NewEstimateId(uuidToString(row.EstimateID))
	if err != nil {
		return nil, err
	}
	origin, err := shared.NewLocation(row.OriginUnlocode)
	if err != nil {
		return nil, err
	}
	dest, err := shared.NewLocation(row.DestinationUnlocode)
	if err != nil {
		return nil, err
	}
	candidates := make([]domain.RouteCandidate, 0, len(cands))
	for _, c := range cands {
		var waypoints []string
		if c.Waypoints != "" {
			waypoints = strings.Split(c.Waypoints, ",")
		}
		rc, err := domain.NewRouteCandidate(c.VoyageNumber, int(c.TransitDays), c.EstimatedCost, waypoints)
		if err != nil {
			return nil, err
		}
		candidates = append(candidates, rc)
	}
	return domain.Restore(domain.EstimateSnapshot{
		Id:              id,
		Origin:          origin,
		Destination:     dest,
		ArrivalDeadline: row.ArrivalDeadline.Time,
		CargoType:       shared.CargoType(row.CargoType),
		WeightKg:        floatFromNumeric(row.WeightKg),
		Candidates:      candidates,
		Status:          domain.EstimateStatus(row.Status),
	}), nil
}

// parseUUID は文字列 UUID を pgtype.UUID に変換する。
func parseUUID(s string) (pgtype.UUID, error) {
	var u pgtype.UUID
	err := u.Scan(s)
	return u, err
}

// uuidToString は pgtype.UUID を文字列に変換する。
func uuidToString(u pgtype.UUID) string {
	b := u.Bytes
	const hextable = "0123456789abcdef"
	buf := make([]byte, 36)
	for i, ti := range []int{0, 2, 4, 6, 9, 11, 14, 16, 19, 21, 24, 26, 28, 30, 32, 34} {
		buf[ti] = hextable[b[i]>>4]
		buf[ti+1] = hextable[b[i]&0x0f]
	}
	buf[8], buf[13], buf[18], buf[23] = '-', '-', '-', '-'
	return string(buf)
}

// safeInt32 は int を int32 へ安全変換する（範囲外はクランプ）。
func safeInt32(v int) int32 {
	if v > math.MaxInt32 {
		return math.MaxInt32
	}
	if v < math.MinInt32 {
		return math.MinInt32
	}
	return int32(v)
}

// numericFromFloat3 は小数第 3 位までの値を pgtype.Numeric に変換する。
func numericFromFloat3(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	n.Int = big.NewInt(int64(math.Round(f * 1000)))
	n.Exp = -3
	n.Valid = true
	return n
}

// floatFromNumeric は pgtype.Numeric を float64 に変換する。
func floatFromNumeric(n pgtype.Numeric) float64 {
	if !n.Valid || n.Int == nil {
		return 0
	}
	f := new(big.Float).SetInt(n.Int)
	for i := int32(0); i < -n.Exp; i++ {
		f.Quo(f, big.NewFloat(10))
	}
	v, _ := f.Float64()
	return v
}
