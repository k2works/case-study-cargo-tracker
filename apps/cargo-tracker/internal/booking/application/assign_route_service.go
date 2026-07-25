package application

import (
	"context"
	"errors"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

// ErrCandidateOutOfRange は選択された経路候補の添字が範囲外の場合に返される（US09）。
var ErrCandidateOutOfRange = errors.New("selected route candidate is out of range")

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
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return nil, err
	}
	return s.searcher.Search(ctx, specFrom(cargo))
}

// Assign は算出候補のうち index 番目を選択し、確定経路として Cargo に割り当てる（US09）。
// 探索は Candidates と同一の決定的アルゴリズムのため、index は安定して同じ候補を指す。
func (s *AssignRouteService) Assign(ctx context.Context, bookingID domain.BookingId, index int) error {
	cargo, err := s.repo.FindByBookingID(ctx, bookingID)
	if err != nil {
		return err
	}
	candidates, err := s.searcher.Search(ctx, specFrom(cargo))
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
