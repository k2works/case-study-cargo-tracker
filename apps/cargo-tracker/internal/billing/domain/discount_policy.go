package domain

// DiscountPolicy は割引率の適用ルールを表す値オブジェクト（US22）。
// 法人荷主には契約割引率を、個人荷主には割引なし（0%）を適用する。
type DiscountPolicy struct{}

// CalculateRate は荷主種別と契約割引率から適用割引率を算出する。
// 法人（CORPORATE）は契約割引率、個人（INDIVIDUAL）は 0% を返す。
func (DiscountPolicy) CalculateRate(shipperId BillingShipperId, contractRate DiscountRate) DiscountRate {
	if shipperId.IsCorporate() {
		return contractRate
	}
	return DiscountRate{rate: 0}
}
