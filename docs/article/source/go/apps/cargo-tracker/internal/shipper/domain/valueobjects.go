// Package domain は Shipper Context のドメインモデル（集約・値オブジェクト）を提供する。
package domain

import (
	"errors"
	"strings"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ドメインエラー。
var (
	ErrEmptyShipperName       = errors.New("shipper name must not be empty")
	ErrInvalidEmail           = errors.New("email must be a valid address")
	ErrDiscountRateOutOfRange = errors.New("discount rate must be between 0 and 0.30")
	ErrEmptyContractNumber    = errors.New("contract number must not be empty")
	ErrUnknownShipperType     = errors.New("unknown shipper type")
)

// ShipperType は荷主種別（個人・法人）を表す列挙型。
type ShipperType string

const (
	// ShipperTypeIndividual は個人荷主。
	ShipperTypeIndividual ShipperType = "INDIVIDUAL"
	// ShipperTypeCorporate は法人荷主。
	ShipperTypeCorporate ShipperType = "CORPORATE"
)

// ParseShipperType は文字列から ShipperType を解釈する。
func ParseShipperType(v string) (ShipperType, error) {
	switch ShipperType(v) {
	case ShipperTypeIndividual:
		return ShipperTypeIndividual, nil
	case ShipperTypeCorporate:
		return ShipperTypeCorporate, nil
	default:
		return "", ErrUnknownShipperType
	}
}

// ShipperCode は自動生成される荷主の業務識別コード（SHP- プレフィックス）。
type ShipperCode struct {
	value string
}

// GenerateShipperCode は生の UUID から SHP- プレフィックス + 先頭 8 文字（大文字）のコードを生成する。
func GenerateShipperCode(rawUUID string) ShipperCode {
	return ShipperCode{value: shared.GenerateBusinessCode("SHP", rawUUID)}
}

// Value は荷主コードの文字列表現を返す。
func (c ShipperCode) Value() string { return c.value }

// ShipperName は荷主の氏名または社名を表す値オブジェクト。
type ShipperName struct {
	value string
}

// NewShipperName はバリデーション付きで ShipperName を生成する。
func NewShipperName(v string) (ShipperName, error) {
	if strings.TrimSpace(v) == "" {
		return ShipperName{}, ErrEmptyShipperName
	}
	return ShipperName{value: v}, nil
}

// Value は荷主名の文字列表現を返す。
func (n ShipperName) Value() string { return n.value }

// Email はメールアドレスを表す値オブジェクト。一意制約はリポジトリ層で担保する。
type Email struct {
	value string
}

// NewEmail は簡易バリデーション付きで Email を生成する。
func NewEmail(v string) (Email, error) {
	at := strings.Index(v, "@")
	if at <= 0 || at == len(v)-1 || !strings.Contains(v[at+1:], ".") {
		return Email{}, ErrInvalidEmail
	}
	return Email{value: v}, nil
}

// Value はメールアドレスの文字列表現を返す。
func (e Email) Value() string { return e.value }

// Phone は電話番号を表す値オブジェクト（オプション）。
type Phone struct {
	value string
}

// NewPhone は Phone を生成する。
func NewPhone(v string) Phone { return Phone{value: v} }

// Value は電話番号の文字列表現を返す。
func (p Phone) Value() string { return p.value }

// Address は住所を表す値オブジェクト（オプション、最大 500 文字）。
type Address struct {
	value string
}

// ErrAddressTooLong は住所が 500 文字を超えた場合に返される。
var ErrAddressTooLong = errors.New("address must be at most 500 characters")

// NewAddress はバリデーション付きで Address を生成する。
func NewAddress(v string) (Address, error) {
	if len([]rune(v)) > 500 {
		return Address{}, ErrAddressTooLong
	}
	return Address{value: v}, nil
}

// Value は住所の文字列表現を返す。
func (a Address) Value() string { return a.value }

// ContractNumber は法人荷主の契約番号を表す値オブジェクト。
type ContractNumber struct {
	value string
}

// NewContractNumber はバリデーション付きで ContractNumber を生成する。
func NewContractNumber(v string) (ContractNumber, error) {
	if strings.TrimSpace(v) == "" {
		return ContractNumber{}, ErrEmptyContractNumber
	}
	return ContractNumber{value: v}, nil
}

// Value は契約番号の文字列表現を返す。
func (c ContractNumber) Value() string { return c.value }

// DiscountRate は法人荷主の割引率（0〜30%）を表す値オブジェクト。
type DiscountRate struct {
	value float64
}

// NewDiscountRate は 0.0〜0.30 の範囲でバリデーションして DiscountRate を生成する。
func NewDiscountRate(v float64) (DiscountRate, error) {
	if v < 0 || v > 0.30 {
		return DiscountRate{}, ErrDiscountRateOutOfRange
	}
	return DiscountRate{value: v}, nil
}

// Value は割引率（0.0〜0.30）を返す。
func (d DiscountRate) Value() float64 { return d.value }

// CorporateContract は法人荷主の契約情報（契約番号・割引率）を保持する値オブジェクト。
type CorporateContract struct {
	number       ContractNumber
	discountRate DiscountRate
}

// NewCorporateContract は CorporateContract を生成する。
func NewCorporateContract(number ContractNumber, rate DiscountRate) CorporateContract {
	return CorporateContract{number: number, discountRate: rate}
}

// Number は契約番号を返す。
func (c CorporateContract) Number() ContractNumber { return c.number }

// DiscountRate は割引率を返す。
func (c CorporateContract) DiscountRate() DiscountRate { return c.discountRate }
