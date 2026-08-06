package application_test

import (
	"context"
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubItineraryRepo struct {
	cargo                *domain.Cargo
	saved                *domain.Cargo
	routingStatusUpdated bool
}

func (s *stubItineraryRepo) FindByBookingID(_ context.Context, _ domain.BookingId) (*domain.Cargo, error) {
	if s.cargo == nil {
		return nil, domain.ErrCargoNotFound
	}
	return s.cargo, nil
}
func (s *stubItineraryRepo) SaveItinerary(_ context.Context, c *domain.Cargo) error {
	s.saved = c
	return nil
}
func (s *stubItineraryRepo) UpdateRoutingStatus(_ context.Context, c *domain.Cargo) error {
	s.saved = c
	s.routingStatusUpdated = true
	return nil
}

type stubSearcher struct {
	candidates []application.RouteCandidateDTO
	gotSpec    application.RouteSearchSpec
}

func (s *stubSearcher) Search(_ context.Context, spec application.RouteSearchSpec) ([]application.RouteCandidateDTO, error) {
	s.gotSpec = spec
	return s.candidates, nil
}

func dt(y, m, d int) time.Time { return time.Date(y, time.Month(m), d, 0, 0, 0, 0, time.UTC) }

func proposedCargo(t *testing.T) *domain.Cargo {
	t.Helper()
	origin, _ := shared.NewLocation("JPTYO")
	dest, _ := shared.NewLocation("USLAX")
	spec, err := domain.NewRouteSpecification(origin, dest, dt(2026, 12, 1))
	require.NoError(t, err)
	w, _ := domain.NewWeight(1000)
	id, _ := domain.NewBookingId("BINT-0001")
	sc, _ := shared.NewShipperCode("SH01")
	c, err := domain.RegisterCargo(domain.CargoParams{
		BookingID: id, ShipperCode: sc, RouteSpec: spec,
		CargoType: shared.CargoTypeGeneral, Weight: w, BookingAmount: domain.NewMoney(100000, "JPY"),
	})
	require.NoError(t, err)
	require.NoError(t, c.AssignToRouting()) // ROUTE_PROPOSED
	return c
}

func directCandidate() application.RouteCandidateDTO {
	return application.RouteCandidateDTO{
		Legs: []application.RouteLegDTO{
			{VoyageNumber: "V-DIRECT", LoadUnLocode: "JPTYO", UnloadUnLocode: "USLAX", LoadTime: dt(2026, 10, 1), UnloadTime: dt(2026, 10, 13)},
		},
		TransitDays: 12, EstimatedCost: 160000,
	}
}

func TestAssignRouteService_Candidates(t *testing.T) {
	repo := &stubItineraryRepo{cargo: proposedCargo(t)}
	searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
	svc := application.NewAssignRouteService(repo, searcher)

	id, _ := domain.NewBookingId("BINT-0001")
	got, err := svc.Candidates(context.Background(), id)
	require.NoError(t, err)
	require.Len(t, got, 1)
	// 探索仕様は cargo の routeSpec・貨物種別から組み立てられる
	assert.Equal(t, "JPTYO", searcher.gotSpec.OriginUnLocode)
	assert.Equal(t, "USLAX", searcher.gotSpec.DestinationUnLocode)
	assert.Equal(t, "GENERAL", searcher.gotSpec.CargoType)
}

func TestAssignRouteService_Assign(t *testing.T) {
	t.Run("候補を選択して経路確定できる", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)

		id, _ := domain.NewBookingId("BINT-0001")
		require.NoError(t, svc.Assign(context.Background(), id, 0))
		require.NotNil(t, repo.saved)
		assert.Equal(t, shared.RoutingStatusRouted, repo.saved.RoutingStatus())
		require.NotNil(t, repo.saved.Itinerary())
		assert.Len(t, repo.saved.Itinerary().Legs(), 1)
	})
	t.Run("範囲外の候補選択はエラー", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)
		id, _ := domain.NewBookingId("BINT-0001")
		err := svc.Assign(context.Background(), id, 5)
		require.ErrorIs(t, err, application.ErrCandidateOutOfRange)
	})
	t.Run("引き渡し前の予約は割り当て不可", func(t *testing.T) {
		origin, _ := shared.NewLocation("JPTYO")
		dest, _ := shared.NewLocation("USLAX")
		spec, _ := domain.NewRouteSpecification(origin, dest, dt(2026, 12, 1))
		w, _ := domain.NewWeight(1000)
		id, _ := domain.NewBookingId("BINT-0002")
		sc, _ := shared.NewShipperCode("SH01")
		prelim, _ := domain.RegisterCargo(domain.CargoParams{
			BookingID: id, ShipperCode: sc, RouteSpec: spec,
			CargoType: shared.CargoTypeGeneral, Weight: w, BookingAmount: domain.NewMoney(100000, "JPY"),
		})
		repo := &stubItineraryRepo{cargo: prelim}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)
		err := svc.Assign(context.Background(), id, 0)
		require.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
	})
}

func TestAssignRouteService_Adjustment(t *testing.T) {
	t.Run("期限オーバーライドで探索仕様の期限が置き換わる", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)
		id, _ := domain.NewBookingId("BINT-0001")

		newDeadline := dt(2027, 3, 1)
		_, err := svc.CandidatesWithAdjustment(context.Background(), id, application.RouteAdjustment{OverrideDeadline: newDeadline})
		require.NoError(t, err)
		assert.Equal(t, newDeadline, searcher.gotSpec.ArrivalDeadline)
	})
	t.Run("オーバーライド無しは cargo の期限を使う", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)
		id, _ := domain.NewBookingId("BINT-0001")

		_, err := svc.CandidatesWithAdjustment(context.Background(), id, application.RouteAdjustment{})
		require.NoError(t, err)
		assert.Equal(t, dt(2026, 12, 1), searcher.gotSpec.ArrivalDeadline)
	})
	t.Run("調整後の候補を選択して確定できる", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)
		id, _ := domain.NewBookingId("BINT-0001")

		err := svc.AssignWithAdjustment(context.Background(), id, 0, application.RouteAdjustment{OverrideDeadline: dt(2027, 3, 1)})
		require.NoError(t, err)
		require.NotNil(t, repo.saved)
		assert.Equal(t, shared.RoutingStatusRouted, repo.saved.RoutingStatus())
	})
}

func TestAssignRouteService_Readjust(t *testing.T) {
	t.Run("ROUTED を MISROUTED にして永続化する", func(t *testing.T) {
		c := proposedCargo(t)
		it, _ := domain.NewCargoItinerary([]domain.Leg{directLeg(t)})
		require.NoError(t, c.AssignItinerary(it))
		repo := &stubItineraryRepo{cargo: c}
		svc := application.NewAssignRouteService(repo, &stubSearcher{})
		id, _ := domain.NewBookingId("BINT-0001")
		require.NoError(t, svc.Readjust(context.Background(), id))
		require.NotNil(t, repo.saved)
		assert.Equal(t, shared.RoutingStatusMisrouted, repo.saved.RoutingStatus())
	})
	t.Run("未確定は冪等（何もしない）", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)} // NOT_ROUTED
		svc := application.NewAssignRouteService(repo, &stubSearcher{})
		id, _ := domain.NewBookingId("BINT-0001")
		require.NoError(t, svc.Readjust(context.Background(), id))
		assert.Nil(t, repo.saved)
	})
	t.Run("MISROUTED 後に再割り当てで ROUTED に戻せる", func(t *testing.T) {
		c := proposedCargo(t)
		it, _ := domain.NewCargoItinerary([]domain.Leg{directLeg(t)})
		require.NoError(t, c.AssignItinerary(it))
		repo := &stubItineraryRepo{cargo: c}
		searcher := &stubSearcher{candidates: []application.RouteCandidateDTO{directCandidate()}}
		svc := application.NewAssignRouteService(repo, searcher)
		id, _ := domain.NewBookingId("BINT-0001")
		require.NoError(t, svc.Readjust(context.Background(), id))
		require.NoError(t, svc.Assign(context.Background(), id, 0))
		assert.Equal(t, shared.RoutingStatusRouted, repo.saved.RoutingStatus())
	})
}

func directLeg(t *testing.T) domain.Leg {
	t.Helper()
	o, _ := shared.NewLocation("JPTYO")
	d, _ := shared.NewLocation("USLAX")
	l, err := domain.NewLeg("V-DIRECT", o, d, dt(2026, 10, 1), dt(2026, 10, 13))
	require.NoError(t, err)
	return l
}
