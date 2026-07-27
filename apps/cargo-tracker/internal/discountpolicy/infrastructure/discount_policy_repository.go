// Package infrastructure は Discount Policy Context の出力アダプター（Repository）を提供する。
package infrastructure

import (
	"context"
	"errors"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/discountpolicy/domain"
)

// DiscountPolicyRepository は pgx による割引ポリシーリポジトリ実装。
type DiscountPolicyRepository struct {
	pool *pgxpool.Pool
}

// NewDiscountPolicyRepository は DiscountPolicyRepository を生成する。
func NewDiscountPolicyRepository(pool *pgxpool.Pool) *DiscountPolicyRepository {
	return &DiscountPolicyRepository{pool: pool}
}

const upsertSQL = `
INSERT INTO discount_policy (id, policy_type, discount_rate, valid_from, valid_until, description, updated_at)
VALUES ($1, $2, $3, $4, $5, $6, NOW())
ON CONFLICT (id) DO UPDATE SET
    policy_type = EXCLUDED.policy_type,
    discount_rate = EXCLUDED.discount_rate,
    valid_from = EXCLUDED.valid_from,
    valid_until = EXCLUDED.valid_until,
    description = EXCLUDED.description,
    updated_at = NOW()
`

// Save は割引ポリシーを永続化する（id の重複は更新）。
func (r *DiscountPolicyRepository) Save(ctx context.Context, p *domain.DiscountPolicy) error {
	_, err := r.pool.Exec(ctx, upsertSQL,
		p.ID(),
		string(p.PolicyType()),
		numericFromFloat(p.Rate()),
		pgtype.Date{Time: p.ValidFrom(), Valid: true},
		nullableDate(p.ValidUntil()),
		p.Description(),
	)
	return err
}

const selectColumns = `id, policy_type, discount_rate, valid_from, valid_until, description`

// FindByID は ID 指定で割引ポリシーを復元する。存在しなければ ErrPolicyNotFound。
func (r *DiscountPolicyRepository) FindByID(ctx context.Context, id string) (*domain.DiscountPolicy, error) {
	row := r.pool.QueryRow(ctx, `SELECT `+selectColumns+` FROM discount_policy WHERE id = $1`, id)
	p, err := scanPolicy(row)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, application.ErrPolicyNotFound
		}
		return nil, err
	}
	return p, nil
}

// List は割引ポリシー一覧を有効開始日降順で返す。
func (r *DiscountPolicyRepository) List(ctx context.Context) ([]*domain.DiscountPolicy, error) {
	rows, err := r.pool.Query(ctx, `SELECT `+selectColumns+` FROM discount_policy ORDER BY valid_from DESC, id`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []*domain.DiscountPolicy
	for rows.Next() {
		p, err := scanPolicy(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

type scanner interface {
	Scan(dest ...any) error
}

func scanPolicy(row scanner) (*domain.DiscountPolicy, error) {
	var (
		id          string
		policyType  string
		rate        pgtype.Numeric
		validFrom   pgtype.Date
		validUntil  pgtype.Date
		description string
	)
	if err := row.Scan(&id, &policyType, &rate, &validFrom, &validUntil, &description); err != nil {
		return nil, err
	}
	var until *time.Time
	if validUntil.Valid {
		t := validUntil.Time
		until = &t
	}
	return domain.Reconstruct(domain.NewDiscountPolicyParams{
		ID:          id,
		PolicyType:  domain.PolicyType(policyType),
		Rate:        numericToFloat(rate),
		ValidFrom:   validFrom.Time,
		ValidUntil:  until,
		Description: description,
	}), nil
}

func nullableDate(t *time.Time) pgtype.Date {
	if t == nil {
		return pgtype.Date{Valid: false}
	}
	return pgtype.Date{Time: *t, Valid: true}
}

func numericFromFloat(f float64) pgtype.Numeric {
	var n pgtype.Numeric
	_ = n.Scan(strconv.FormatFloat(f, 'f', 4, 64))
	return n
}

func numericToFloat(n pgtype.Numeric) float64 {
	if !n.Valid {
		return 0
	}
	v, err := n.Float64Value()
	if err != nil {
		return 0
	}
	return v.Float64
}
