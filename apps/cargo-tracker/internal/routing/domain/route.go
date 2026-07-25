package domain

import (
	"errors"
	"sort"
	"time"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// 経路探索のドメインエラー。
var (
	ErrSameSpecOriginDestination = errors.New("route specification origin and destination must differ")
)

// maxRouteLegs は経路探索の最大区間数（段階実装: 直行→乗り継ぎの探索深度上限）。
const maxRouteLegs = 4

// RouteSpecification は経路探索の要求仕様（出発地・目的地・到着期限・貨物種別）を表す値オブジェクト。
type RouteSpecification struct {
	origin          shared.Location
	destination     shared.Location
	arrivalDeadline time.Time
	cargoType       shared.CargoType
}

// NewRouteSpecification はバリデーション付きで経路探索仕様を生成する。
func NewRouteSpecification(origin, destination shared.Location, arrivalDeadline time.Time, cargoType shared.CargoType) (RouteSpecification, error) {
	if origin.SameAs(destination) {
		return RouteSpecification{}, ErrSameSpecOriginDestination
	}
	return RouteSpecification{origin: origin, destination: destination, arrivalDeadline: arrivalDeadline, cargoType: cargoType}, nil
}

// Origin は出発地を返す。
func (s RouteSpecification) Origin() shared.Location { return s.origin }

// Destination は目的地を返す。
func (s RouteSpecification) Destination() shared.Location { return s.destination }

// ArrivalDeadline は到着期限を返す。
func (s RouteSpecification) ArrivalDeadline() time.Time { return s.arrivalDeadline }

// CargoType は貨物種別を返す。
func (s RouteSpecification) CargoType() shared.CargoType { return s.cargoType }

// Leg は確定経路を構成する 1 区間（航海番号・積込/荷降地・積込/荷降時刻）を表す値オブジェクト。
type Leg struct {
	voyageNumber VoyageNumber
	load         shared.Location
	unload       shared.Location
	loadTime     time.Time
	unloadTime   time.Time
}

// NewLeg は Leg を生成する。
func NewLeg(voyageNumber VoyageNumber, load, unload shared.Location, loadTime, unloadTime time.Time) Leg {
	return Leg{voyageNumber: voyageNumber, load: load, unload: unload, loadTime: loadTime, unloadTime: unloadTime}
}

// VoyageNumber は航海番号を返す。
func (l Leg) VoyageNumber() VoyageNumber { return l.voyageNumber }

// Load は積込地を返す。
func (l Leg) Load() shared.Location { return l.load }

// Unload は荷降地を返す。
func (l Leg) Unload() shared.Location { return l.unload }

// LoadTime は積込時刻を返す。
func (l Leg) LoadTime() time.Time { return l.loadTime }

// UnloadTime は荷降時刻を返す。
func (l Leg) UnloadTime() time.Time { return l.unloadTime }

// RouteCandidate は経路探索が算出した経路候補（Leg 列・所要日数・概算費用）を表す値オブジェクト。
type RouteCandidate struct {
	legs []Leg
}

func newRouteCandidate(legs []Leg) RouteCandidate {
	cp := make([]Leg, len(legs))
	copy(cp, legs)
	return RouteCandidate{legs: cp}
}

// Legs は区間の並びを返す（防御的コピー）。
func (c RouteCandidate) Legs() []Leg {
	cp := make([]Leg, len(c.legs))
	copy(cp, c.legs)
	return cp
}

// Origin は経路の起点を返す。
func (c RouteCandidate) Origin() shared.Location { return c.legs[0].load }

// Destination は経路の終点を返す。
func (c RouteCandidate) Destination() shared.Location { return c.legs[len(c.legs)-1].unload }

// DepartureTime は最初の積込時刻を返す。
func (c RouteCandidate) DepartureTime() time.Time { return c.legs[0].loadTime }

// ArrivalTime は最後の荷降時刻を返す。
func (c RouteCandidate) ArrivalTime() time.Time { return c.legs[len(c.legs)-1].unloadTime }

// Waypoints は経由港（中間の荷降地＝次の積込地）の並びを返す。
func (c RouteCandidate) Waypoints() []shared.Location {
	wp := make([]shared.Location, 0, len(c.legs)-1)
	for i := 0; i < len(c.legs)-1; i++ {
		wp = append(wp, c.legs[i].unload)
	}
	return wp
}

// TransitDays は起点出発から終点到着までの所要日数を返す。
func (c RouteCandidate) TransitDays() int {
	d := c.ArrivalTime().Sub(c.DepartureTime())
	return int(d.Hours() / 24)
}

// EstimatedCost は概算費用を返す（区間数・所要日数ベースの簡易モデル）。
func (c RouteCandidate) EstimatedCost() int64 {
	return int64(len(c.legs))*100000 + int64(c.TransitDays())*5000
}

// RouteFinder は航海スケジュール群を接続グラフとみなし経路候補を算出するドメインサービス（US08）。
type RouteFinder struct{}

// legEdge は探索グラフの辺（ある航海のある区間）。
type legEdge struct {
	voyage   VoyageNumber
	movement CarrierMovement
}

// FindCandidates は spec を満たす経路候補を推奨順（区間数少→所要日数→費用）で返す。
// 空間連結（前区間の荷降地＝次区間の積込地）・時刻連続（次の積込は前の荷降以降）・
// 貨物種別対応・到着期限内を満たす経路のみを採用する。
func (RouteFinder) FindCandidates(spec RouteSpecification, voyages []*Voyage) []RouteCandidate {
	edges := buildEdges(spec.CargoType(), voyages)

	var results []RouteCandidate
	visited := map[string]bool{spec.Origin().UnLocode(): true}
	searchRoutes(spec, edges, spec.Origin(), time.Time{}, nil, visited, &results)

	sortCandidates(results)
	return results
}

// buildEdges は貨物種別に対応する航海の全区間を探索用の辺集合に変換する。
func buildEdges(cargoType shared.CargoType, voyages []*Voyage) []legEdge {
	var edges []legEdge
	for _, v := range voyages {
		if !v.Supports(cargoType) {
			continue
		}
		for _, m := range v.Schedule().Movements() {
			edges = append(edges, legEdge{voyage: v.VoyageNumber(), movement: m})
		}
	}
	return edges
}

// searchRoutes は深さ優先で経路を列挙する（visited による循環回避・maxRouteLegs で深度制限）。
func searchRoutes(spec RouteSpecification, edges []legEdge, current shared.Location, earliest time.Time, path []Leg, visited map[string]bool, results *[]RouteCandidate) {
	if len(path) >= maxRouteLegs {
		return
	}
	for _, e := range edges {
		if !e.movement.Departure().SameAs(current) {
			continue
		}
		if !earliest.IsZero() && e.movement.DepartureTime().Before(earliest) {
			continue // 時刻連続性: 前区間の荷降より前には積込できない
		}
		if exceedsDeadline(spec.ArrivalDeadline(), e.movement.ArrivalTime()) {
			continue // 到着期限超過の枝を刈る
		}
		leg := NewLeg(e.voyage, e.movement.Departure(), e.movement.Arrival(), e.movement.DepartureTime(), e.movement.ArrivalTime())
		newPath := append(append([]Leg{}, path...), leg)

		if e.movement.Arrival().SameAs(spec.Destination()) {
			*results = append(*results, newRouteCandidate(newPath))
			continue
		}
		if visited[e.movement.Arrival().UnLocode()] {
			continue // 循環回避
		}
		nv := cloneVisited(visited)
		nv[e.movement.Arrival().UnLocode()] = true
		searchRoutes(spec, edges, e.movement.Arrival(), e.movement.ArrivalTime(), newPath, nv, results)
	}
}

func exceedsDeadline(deadline, arrival time.Time) bool {
	return !deadline.IsZero() && arrival.After(deadline)
}

func cloneVisited(src map[string]bool) map[string]bool {
	dst := make(map[string]bool, len(src)+1)
	for k, v := range src {
		dst[k] = v
	}
	return dst
}

// sortCandidates は推奨順（区間数少→所要日数→費用→出発時刻）に並べ替える（直行便を最優先）。
func sortCandidates(candidates []RouteCandidate) {
	sort.SliceStable(candidates, func(i, j int) bool {
		a, b := candidates[i], candidates[j]
		if len(a.legs) != len(b.legs) {
			return len(a.legs) < len(b.legs)
		}
		if a.TransitDays() != b.TransitDays() {
			return a.TransitDays() < b.TransitDays()
		}
		if a.EstimatedCost() != b.EstimatedCost() {
			return a.EstimatedCost() < b.EstimatedCost()
		}
		return a.DepartureTime().Before(b.DepartureTime())
	})
}
