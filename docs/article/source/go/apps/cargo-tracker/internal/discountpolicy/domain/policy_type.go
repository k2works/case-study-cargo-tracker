// Package domain は Discount Policy Context（割引ポリシー管理・US-ADM-01）のドメインモデルを提供する。
// 管理者が割引方針を登録・編集する管理コンテキストであり、Billing の割引率算出（VO）とは
// 独立した集約として割引ポリシーを永続化・管理する。
package domain

// PolicyType は割引方針の種別（domain-model.md DiscountPolicyType と一致）。
type PolicyType string

const (
	// PolicyTypeCorporateStandard は法人標準割引。
	PolicyTypeCorporateStandard PolicyType = "CORPORATE_STANDARD"
	// PolicyTypeVolumeDiscount はボリューム（数量）割引。
	PolicyTypeVolumeDiscount PolicyType = "VOLUME_DISCOUNT"
	// PolicyTypeSeasonal はシーズン割引。
	PolicyTypeSeasonal PolicyType = "SEASONAL"
	// PolicyTypeNone は割引なし。
	PolicyTypeNone PolicyType = "NONE"
)

// Valid は既知の割引方針種別かを返す。
func (t PolicyType) Valid() bool {
	switch t {
	case PolicyTypeCorporateStandard, PolicyTypeVolumeDiscount, PolicyTypeSeasonal, PolicyTypeNone:
		return true
	default:
		return false
	}
}

// Ja は日本語表示名を返す。
func (t PolicyType) Ja() string {
	switch t {
	case PolicyTypeCorporateStandard:
		return "法人標準割引"
	case PolicyTypeVolumeDiscount:
		return "ボリューム割引"
	case PolicyTypeSeasonal:
		return "シーズン割引"
	case PolicyTypeNone:
		return "割引なし"
	default:
		return string(t)
	}
}

// AllPolicyTypes は選択肢表示用の全種別を返す。
func AllPolicyTypes() []PolicyType {
	return []PolicyType{
		PolicyTypeCorporateStandard,
		PolicyTypeVolumeDiscount,
		PolicyTypeSeasonal,
		PolicyTypeNone,
	}
}
