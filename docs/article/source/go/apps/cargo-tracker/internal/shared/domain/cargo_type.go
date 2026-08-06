package domain

import "errors"

// ErrUnknownCargoType は未知の貨物種別を解釈しようとした場合に返される。
var ErrUnknownCargoType = errors.New("unknown cargo type")

// CargoType は貨物種別を表す共有カーネルの列挙型。
// Booking / Estimation / Routing の各コンテキストで共通に用いる（ADR-0006）。
type CargoType string

const (
	// CargoTypeGeneral は一般貨物。
	CargoTypeGeneral CargoType = "GENERAL"
	// CargoTypeHazardous は危険物。
	CargoTypeHazardous CargoType = "HAZARDOUS"
	// CargoTypeRefrigerated は冷凍・冷蔵貨物。
	CargoTypeRefrigerated CargoType = "REFRIGERATED"
)

// ParseCargoType は文字列から CargoType を解釈する。
func ParseCargoType(v string) (CargoType, error) {
	switch CargoType(v) {
	case CargoTypeGeneral, CargoTypeHazardous, CargoTypeRefrigerated:
		return CargoType(v), nil
	default:
		return "", ErrUnknownCargoType
	}
}
