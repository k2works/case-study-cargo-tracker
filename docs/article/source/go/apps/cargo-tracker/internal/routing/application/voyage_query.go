package application

import (
	"context"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/routing/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// VoyageView は航海一覧・検索結果の表示用 DTO。
type VoyageView struct {
	VoyageNumber string
	VesselName   string
	Carrier      string
	Origin       string
	Destination  string
	DepartureAt  time.Time
	ArrivalAt    time.Time
	CargoTypes   []string
	Waypoints    []string
	Movements    []MovementView
}

// MovementView は運送区間の表示用 DTO（edit フォームで全区間を再表示するために保持）。
type MovementView struct {
	Departure     string
	Arrival       string
	DepartureDate string
	ArrivalDate   string
}

// SearchVoyageCriteria は航海検索の条件（US07）。空欄の条件は無視される。
type SearchVoyageCriteria struct {
	OriginUnLocode      string
	DestinationUnLocode string
	CargoType           string
	DepartureFrom       time.Time
	DepartureTo         time.Time
}

// VoyageQueryService は航海の照会・検索ユースケース。
type VoyageQueryService struct {
	repo VoyageRepository
}

// NewVoyageQueryService は VoyageQueryService を生成する。
func NewVoyageQueryService(repo VoyageRepository) *VoyageQueryService {
	return &VoyageQueryService{repo: repo}
}

// SearchVoyageService は VoyageQueryService の検索用途エイリアス（テスト可読性のため）。
type SearchVoyageService = VoyageQueryService

// NewSearchVoyageService は SearchVoyageService を生成する。
func NewSearchVoyageService(repo VoyageRepository) *SearchVoyageService {
	return NewVoyageQueryService(repo)
}

// List は全航海を表示用 DTO で返す。
func (s *VoyageQueryService) List(ctx context.Context) ([]VoyageView, error) {
	voyages, err := s.repo.ListAll(ctx)
	if err != nil {
		return nil, err
	}
	views := make([]VoyageView, 0, len(voyages))
	for _, v := range voyages {
		views = append(views, toVoyageView(v))
	}
	return views, nil
}

// Search は条件（出発地・目的地・貨物種別・出発期間）で航海を絞り込む（US07）。
// 寄港地接続グラフ探索は US08（IT4）。ここは起点/終点・貨物種別・出発期間の直接フィルタ。
func (s *VoyageQueryService) Search(ctx context.Context, c SearchVoyageCriteria) ([]VoyageView, error) {
	voyages, err := s.repo.ListAll(ctx)
	if err != nil {
		return nil, err
	}
	views := make([]VoyageView, 0)
	for _, v := range voyages {
		if !matchesCriteria(v, c) {
			continue
		}
		views = append(views, toVoyageView(v))
	}
	return views, nil
}

// matchesCriteria は航海が検索条件を満たすかを判定する。
func matchesCriteria(v *domain.Voyage, c SearchVoyageCriteria) bool {
	return matchesEndpoints(v, c) && matchesCargoType(v, c) && matchesDeparture(v, c)
}

// matchesEndpoints は出発地・目的地の一致を判定する。
func matchesEndpoints(v *domain.Voyage, c SearchVoyageCriteria) bool {
	if c.OriginUnLocode != "" && v.Origin().UnLocode() != c.OriginUnLocode {
		return false
	}
	if c.DestinationUnLocode != "" && v.Destination().UnLocode() != c.DestinationUnLocode {
		return false
	}
	return true
}

// matchesCargoType は貨物種別対応を判定する（未指定なら常に真）。
func matchesCargoType(v *domain.Voyage, c SearchVoyageCriteria) bool {
	if c.CargoType == "" {
		return true
	}
	ct, err := shared.ParseCargoType(c.CargoType)
	return err == nil && v.Supports(ct)
}

// matchesDeparture は出発日が指定期間内かを判定する（未指定なら常に真）。
func matchesDeparture(v *domain.Voyage, c SearchVoyageCriteria) bool {
	dep := v.Schedule().Movements()[0].DepartureTime()
	if !c.DepartureFrom.IsZero() && dep.Before(c.DepartureFrom) {
		return false
	}
	if !c.DepartureTo.IsZero() && dep.After(c.DepartureTo) {
		return false
	}
	return true
}

// toVoyageView は Voyage 集約を表示用 DTO へ変換する。
func toVoyageView(v *domain.Voyage) VoyageView {
	movements := v.Schedule().Movements()
	cts := v.SupportedCargoTypes()
	ctStrs := make([]string, len(cts))
	for i, c := range cts {
		ctStrs[i] = string(c)
	}
	waypoints := make([]string, 0, len(movements)+1)
	waypoints = append(waypoints, movements[0].Departure().UnLocode())
	movementViews := make([]MovementView, 0, len(movements))
	for _, m := range movements {
		waypoints = append(waypoints, m.Arrival().UnLocode())
		movementViews = append(movementViews, MovementView{
			Departure:     m.Departure().UnLocode(),
			Arrival:       m.Arrival().UnLocode(),
			DepartureDate: m.DepartureTime().Format("2006-01-02"),
			ArrivalDate:   m.ArrivalTime().Format("2006-01-02"),
		})
	}
	return VoyageView{
		VoyageNumber: v.VoyageNumber().Value(),
		VesselName:   v.VesselName(),
		Carrier:      v.Carrier(),
		Origin:       v.Origin().UnLocode(),
		Destination:  v.Destination().UnLocode(),
		DepartureAt:  movements[0].DepartureTime(),
		ArrivalAt:    movements[len(movements)-1].ArrivalTime(),
		CargoTypes:   ctStrs,
		Waypoints:    waypoints,
		Movements:    movementViews,
	}
}
