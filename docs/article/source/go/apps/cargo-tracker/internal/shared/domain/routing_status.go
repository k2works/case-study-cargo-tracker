package domain

// RoutingStatus は貨物の経路状態を表す共有カーネルの列挙型（ADR-0003）。
type RoutingStatus string

const (
	// RoutingStatusNotRouted は未経路（経路未確定）。
	RoutingStatusNotRouted RoutingStatus = "NOT_ROUTED"
	// RoutingStatusRouted は経路確定済み（CargoItinerary 割り当て済み）。
	RoutingStatusRouted RoutingStatus = "ROUTED"
	// RoutingStatusMisrouted は経路が仕様を満たさなくなった状態。
	RoutingStatusMisrouted RoutingStatus = "MISROUTED"
)

// Ja は経路状態の日本語表示を返す。
func (s RoutingStatus) Ja() string {
	switch s {
	case RoutingStatusNotRouted:
		return "未経路"
	case RoutingStatusRouted:
		return "経路確定"
	case RoutingStatusMisrouted:
		return "経路不整合"
	default:
		return string(s)
	}
}
