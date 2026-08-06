package application

import (
	"context"
	"errors"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrCandidateOutOfRange は選択された経路候補の添字が範囲外の場合に返される（US09）。
var ErrCandidateOutOfRange = errors.New("selected route candidate is out of range")

// RouteAdjustment は経路再算出の条件調整（US10）。ゼロ値は調整なし。
type RouteAdjustment struct {
	// OverrideDeadline は到着期限の上書き（ゼロ値なら cargo の期限を使用）。
	OverrideDeadline time.Time
}

// IsZero は調整が無いかを返す。
func (a RouteAdjustment) IsZero() bool { return a.OverrideDeadline.IsZero() }

// AssignRouteService は経路候補の算出・選択・確定ユースケース（US08/US09）。
// 経路探索は RouteSearcher ポート（ACL）経由で行い、確定経路を Cargo に割り当てる。
type AssignRouteService struct {
	repo     CargoItineraryRepository
	searcher RouteSearcher
}

// NewAssignRouteService は AssignRouteService を生成する。
func NewAssignRouteService(repo CargoItineraryRepository, searcher RouteSearcher) *AssignRouteService {
	return &AssignRouteService{repo: repo, searcher: searcher}
}

// Candidates は予約の要件（出発地・目的地・期限・貨物種別）から経路候補を推奨順で算出する（US08）。
func (s *AssignRouteService) Candidates(ctx context.Context, bookingID domain.BookingId) ([]RouteCandidateDTO, error) {
	return s.CandidatesWithAdjustment(ctx, bookingID, RouteAdjustment{})
}

// CandidatesWithAdjustment は条件調整（期限延長等）を適用して経路候補を再算出する（US10）。
func (s *AssignRouteService) CandidatesWithAdjustment(ctx context.Context, bookingID domain.BookingId, adj RouteAdjustment) ([]RouteCandidateDTO, error) {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return nil, err
	}
	return s.searcher.Search(ctx, adjustedSpec(cargo, adj))
}

// Assign は算出候補のうち index 番目を選択し、確定経路として Cargo に割り当てる（US09）。
func (s *AssignRouteService) Assign(ctx context.Context, bookingID domain.BookingId, index int) error {
	return s.AssignWithAdjustment(ctx, bookingID, index, RouteAdjustment{})
}

// AssignWithAdjustment は条件調整済みの再算出候補のうち index 番目を確定する（US09/US10）。
// 探索は CandidatesWithAdjustment と同一の決定的アルゴリズムのため、index は安定して同じ候補を指す。
func (s *AssignRouteService) AssignWithAdjustment(ctx context.Context, bookingID domain.BookingId, index int, adj RouteAdjustment) error {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return err
	}
	candidates, err := s.searcher.Search(ctx, adjustedSpec(cargo, adj))
	if err != nil {
		return err
	}
	if index < 0 || index >= len(candidates) {
		return ErrCandidateOutOfRange
	}
	itinerary, err := toItinerary(candidates[index])
	if err != nil {
		return err
	}
	if err := cargo.AssignItinerary(itinerary); err != nil {
		return err
	}
	return s.repo.SaveItinerary(ctx, cargo)
}

// Readjust は確定済み（ROUTED）経路を再調整のため MISROUTED にして無効化する（US10）。
// 以降 /route の再算出で新たな候補を選び AssignItinerary で ROUTED に戻す。
// 既に未確定（NOT_ROUTED 等）の場合は何もしない（冪等）。
func (s *AssignRouteService) Readjust(ctx context.Context, bookingID domain.BookingId) error {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return err
	}
	if !cargo.CanMarkMisrouted() {
		return nil // ROUTED でなければ再調整不要（冪等）
	}
	if err := cargo.MarkMisrouted(); err != nil {
		return err
	}
	return s.repo.UpdateRoutingStatus(ctx, cargo)
}

// specFrom は Cargo の要件から経路探索仕様を組み立てる。
func specFrom(cargo *domain.Cargo) RouteSearchSpec {
	spec := cargo.RouteSpec()
	return RouteSearchSpec{
		OriginUnLocode:      spec.Origin().UnLocode(),
		DestinationUnLocode: spec.Destination().UnLocode(),
		ArrivalDeadline:     spec.ArrivalDeadline(),
		CargoType:           string(cargo.CargoType()),
	}
}

// adjustedSpec は cargo の探索仕様に条件調整（期限上書き）を適用する（US10）。
func adjustedSpec(cargo *domain.Cargo, adj RouteAdjustment) RouteSearchSpec {
	spec := specFrom(cargo)
	if !adj.OverrideDeadline.IsZero() {
		spec.ArrivalDeadline = adj.OverrideDeadline
	}
	return spec
}

// toItinerary は経路候補 DTO を確定経路（ドメイン値オブジェクト）へ変換する。
func toItinerary(c RouteCandidateDTO) (domain.CargoItinerary, error) {
	legs := make([]domain.Leg, 0, len(c.Legs))
	for _, l := range c.Legs {
		load, err := shared.NewLocation(l.LoadUnLocode)
		if err != nil {
			return domain.CargoItinerary{}, err
		}
		unload, err := shared.NewLocation(l.UnloadUnLocode)
		if err != nil {
			return domain.CargoItinerary{}, err
		}
		leg, err := domain.NewLeg(l.VoyageNumber, load, unload, l.LoadTime, l.UnloadTime)
		if err != nil {
			return domain.CargoItinerary{}, err
		}
		legs = append(legs, leg)
	}
	return domain.NewCargoItinerary(legs)
}
