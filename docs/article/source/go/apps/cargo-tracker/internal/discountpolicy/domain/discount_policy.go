package domain

import (
	"errors"
	"strings"
	"time"
)

// 割引率の上限（30%）。data-model.md discount_rate CHECK (0.0000〜0.3000) と一致。
const maxRate = 0.30

// ドメイン検証エラー。
var (
	// ErrInvalidRate は割引率が範囲外（0.0〜0.30 の外）であることを示す。
	ErrInvalidRate = errors.New("割引率は 0% 以上 30% 以下で指定してください")
	// ErrInvalidPolicyType は未知の割引方針種別であることを示す。
	ErrInvalidPolicyType = errors.New("不正な割引方針種別です")
	// ErrInvalidPeriod は有効期間の終了が開始より前であることを示す。
	ErrInvalidPeriod = errors.New("有効期限は有効開始日以降で指定してください")
)

// DiscountPolicy は割引ポリシー集約ルート（管理者が登録・編集する割引方針）。
type DiscountPolicy struct {
	id          string
	policyType  PolicyType
	rate        float64 // 割引率（0.0〜0.30 の小数）
	validFrom   time.Time
	validUntil  *time.Time // nil は無期限
	description string
}

// NewDiscountPolicyParams は割引ポリシー生成の入力。
type NewDiscountPolicyParams struct {
	ID          string
	PolicyType  PolicyType
	Rate        float64
	ValidFrom   time.Time
	ValidUntil  *time.Time
	Description string
}

// NewDiscountPolicy は入力を検証して割引ポリシーを生成する。
func NewDiscountPolicy(p NewDiscountPolicyParams) (*DiscountPolicy, error) {
	if !p.PolicyType.Valid() {
		return nil, ErrInvalidPolicyType
	}
	if p.Rate < 0 || p.Rate > maxRate {
		return nil, ErrInvalidRate
	}
	if p.ValidUntil != nil && p.ValidUntil.Before(p.ValidFrom) {
		return nil, ErrInvalidPeriod
	}
	return &DiscountPolicy{
		id:          p.ID,
		policyType:  p.PolicyType,
		rate:        p.Rate,
		validFrom:   p.ValidFrom,
		validUntil:  p.ValidUntil,
		description: strings.TrimSpace(p.Description),
	}, nil
}

// UpdateDiscountPolicyParams は割引ポリシー更新の入力。
type UpdateDiscountPolicyParams struct {
	PolicyType  PolicyType
	Rate        float64
	ValidFrom   time.Time
	ValidUntil  *time.Time
	Description string
}

// Update は割引ポリシーの内容を検証して更新する。
func (d *DiscountPolicy) Update(p UpdateDiscountPolicyParams) error {
	if !p.PolicyType.Valid() {
		return ErrInvalidPolicyType
	}
	if p.Rate < 0 || p.Rate > maxRate {
		return ErrInvalidRate
	}
	if p.ValidUntil != nil && p.ValidUntil.Before(p.ValidFrom) {
		return ErrInvalidPeriod
	}
	d.policyType = p.PolicyType
	d.rate = p.Rate
	d.validFrom = p.ValidFrom
	d.validUntil = p.ValidUntil
	d.description = strings.TrimSpace(p.Description)
	return nil
}

// IsActiveOn は指定日に割引ポリシーが有効かを返す（日付単位で判定）。
// 有効開始日・終了日は DATE 単位のため、時刻付きの date を渡しても
// 開始日当日・終了日当日はいずれも有効と判定する。
func (d *DiscountPolicy) IsActiveOn(date time.Time) bool {
	target := dateOnly(date)
	if target.Before(dateOnly(d.validFrom)) {
		return false
	}
	if d.validUntil != nil && target.After(dateOnly(*d.validUntil)) {
		return false
	}
	return true
}

// dateOnly は時刻を切り捨て日付（同一ロケーションの 00:00）に正規化する。
func dateOnly(t time.Time) time.Time {
	return time.Date(t.Year(), t.Month(), t.Day(), 0, 0, 0, 0, t.Location())
}

// Reconstruct は永続化層から集約を再構築する（検証済み前提）。
func Reconstruct(p NewDiscountPolicyParams) *DiscountPolicy {
	return &DiscountPolicy{
		id:          p.ID,
		policyType:  p.PolicyType,
		rate:        p.Rate,
		validFrom:   p.ValidFrom,
		validUntil:  p.ValidUntil,
		description: p.Description,
	}
}

// ID は割引ポリシー ID を返す。
func (d *DiscountPolicy) ID() string { return d.id }

// PolicyType は割引方針種別を返す。
func (d *DiscountPolicy) PolicyType() PolicyType { return d.policyType }

// Rate は割引率（小数）を返す。
func (d *DiscountPolicy) Rate() float64 { return d.rate }

// ValidFrom は有効開始日を返す。
func (d *DiscountPolicy) ValidFrom() time.Time { return d.validFrom }

// ValidUntil は有効終了日（無期限は nil）を返す。
func (d *DiscountPolicy) ValidUntil() *time.Time { return d.validUntil }

// Description は説明を返す。
func (d *DiscountPolicy) Description() string { return d.description }
