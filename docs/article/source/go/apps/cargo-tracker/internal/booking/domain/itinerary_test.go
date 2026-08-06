package domain_test

import (
	"testing"
	"time"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func legLoc(t *testing.T, v string) shared.Location {
	t.Helper()
	l, err := shared.NewLocation(v)
	require.NoError(t, err)
	return l
}

func day(y, m, d int) time.Time { return time.Date(y, time.Month(m), d, 0, 0, 0, 0, time.UTC) }

func mustLeg(t *testing.T, voyage, load, unload string, lt, ut time.Time) domain.Leg {
	t.Helper()
	l, err := domain.NewLeg(voyage, legLoc(t, load), legLoc(t, unload), lt, ut)
	require.NoError(t, err)
	return l
}

func TestNewLeg(t *testing.T) {
	t.Run("有効な区間を生成できる", func(t *testing.T) {
		l, err := domain.NewLeg("V-DIRECT", legLoc(t, "JPTYO"), legLoc(t, "USLAX"), day(2026, 10, 1), day(2026, 10, 13))
		require.NoError(t, err)
		assert.Equal(t, "V-DIRECT", l.VoyageNumber())
		assert.Equal(t, "JPTYO", l.LoadLocation().UnLocode())
		assert.Equal(t, "USLAX", l.UnloadLocation().UnLocode())
	})
	t.Run("航海番号が空はエラー", func(t *testing.T) {
		_, err := domain.NewLeg("", legLoc(t, "JPTYO"), legLoc(t, "USLAX"), day(2026, 10, 1), day(2026, 10, 13))
		require.ErrorIs(t, err, domain.ErrEmptyLegVoyage)
	})
	t.Run("積込地と荷降地が同一はエラー", func(t *testing.T) {
		_, err := domain.NewLeg("V-DIRECT", legLoc(t, "JPTYO"), legLoc(t, "JPTYO"), day(2026, 10, 1), day(2026, 10, 13))
		require.ErrorIs(t, err, domain.ErrSameLegLoadUnload)
	})
	t.Run("積込時刻が荷降時刻以降はエラー", func(t *testing.T) {
		_, err := domain.NewLeg("V-DIRECT", legLoc(t, "JPTYO"), legLoc(t, "USLAX"), day(2026, 10, 13), day(2026, 10, 1))
		require.ErrorIs(t, err, domain.ErrInvalidLegTimes)
	})
}

func TestNewCargoItinerary(t *testing.T) {
	t.Run("連結した経路を生成できる", func(t *testing.T) {
		it, err := domain.NewCargoItinerary([]domain.Leg{
			mustLeg(t, "V-LEG1", "JPTYO", "SGSIN", day(2026, 10, 1), day(2026, 10, 5)),
			mustLeg(t, "V-LEG2", "SGSIN", "USLAX", day(2026, 10, 6), day(2026, 10, 12)),
		})
		require.NoError(t, err)
		assert.Equal(t, "JPTYO", it.Origin().UnLocode())
		assert.Equal(t, "USLAX", it.Destination().UnLocode())
		assert.Equal(t, day(2026, 10, 12), it.ExpectedArrivalTime())
		assert.Len(t, it.Legs(), 2)
	})
	t.Run("区間が空はエラー", func(t *testing.T) {
		_, err := domain.NewCargoItinerary(nil)
		require.ErrorIs(t, err, domain.ErrEmptyItinerary)
	})
	t.Run("空間が非連結はエラー", func(t *testing.T) {
		_, err := domain.NewCargoItinerary([]domain.Leg{
			mustLeg(t, "V-LEG1", "JPTYO", "SGSIN", day(2026, 10, 1), day(2026, 10, 5)),
			mustLeg(t, "V-LEG2", "HKHKG", "USLAX", day(2026, 10, 6), day(2026, 10, 12)),
		})
		require.ErrorIs(t, err, domain.ErrDisconnectedItinerary)
	})
	t.Run("時刻が逆行はエラー", func(t *testing.T) {
		_, err := domain.NewCargoItinerary([]domain.Leg{
			mustLeg(t, "V-LEG1", "JPTYO", "SGSIN", day(2026, 10, 1), day(2026, 10, 10)),
			mustLeg(t, "V-LEG2", "SGSIN", "USLAX", day(2026, 10, 5), day(2026, 10, 12)),
		})
		require.ErrorIs(t, err, domain.ErrOutOfOrderItinerary)
	})
}

func TestNewDelivery(t *testing.T) {
	d := domain.NewDelivery(shared.RoutingStatusNotRouted)
	assert.Equal(t, shared.RoutingStatusNotRouted, d.RoutingStatus())
	assert.Equal(t, shared.RoutingStatusRouted, domain.NewDelivery(shared.RoutingStatusRouted).RoutingStatus())
}

func routedCargo(t *testing.T) *domain.Cargo {
	t.Helper()
	origin := legLoc(t, "JPTYO")
	dest := legLoc(t, "USLAX")
	spec, err := domain.NewRouteSpecification(origin, dest, day(2026, 12, 1))
	require.NoError(t, err)
	w, err := domain.NewWeight(1000)
	require.NoError(t, err)
	id, err := domain.NewBookingId("BINT-0001")
	require.NoError(t, err)
	sc, err := shared.NewShipperCode("SH01")
	require.NoError(t, err)
	c, err := domain.RegisterCargo(domain.CargoParams{
		BookingID: id, ShipperCode: sc, RouteSpec: spec,
		CargoType: shared.CargoTypeGeneral, Weight: w, BookingAmount: domain.NewMoney(100000, "JPY"),
	})
	require.NoError(t, err)
	return c
}

func TestCargoAssignItinerary(t *testing.T) {
	it, err := domain.NewCargoItinerary([]domain.Leg{
		mustLeg(t, "V-DIRECT", "JPTYO", "USLAX", day(2026, 10, 1), day(2026, 10, 13)),
	})
	require.NoError(t, err)

	t.Run("引き渡し済みなら経路を割り当て ROUTED になる", func(t *testing.T) {
		c := routedCargo(t)
		require.NoError(t, c.AssignToRouting()) // PRELIMINARY → ROUTE_PROPOSED
		require.NoError(t, c.AssignItinerary(it))
		assert.Equal(t, shared.RoutingStatusRouted, c.RoutingStatus())
		require.NotNil(t, c.Itinerary())
		assert.Len(t, c.Itinerary().Legs(), 1)
		// BookingStatus は ROUTE_PROPOSED のまま
		assert.Equal(t, domain.BookingStatusRouteProposed, c.Status())
	})
	t.Run("引き渡し前（PRELIMINARY）は割り当て不可", func(t *testing.T) {
		c := routedCargo(t)
		err := c.AssignItinerary(it)
		require.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
	})
	t.Run("初期状態は NOT_ROUTED", func(t *testing.T) {
		c := routedCargo(t)
		assert.Equal(t, shared.RoutingStatusNotRouted, c.RoutingStatus())
		assert.Nil(t, c.Itinerary())
	})
}

func TestCargoMarkMisrouted(t *testing.T) {
	it, err := domain.NewCargoItinerary([]domain.Leg{
		mustLeg(t, "V-DIRECT", "JPTYO", "USLAX", day(2026, 10, 1), day(2026, 10, 13)),
	})
	require.NoError(t, err)

	t.Run("ROUTED を MISROUTED にできる", func(t *testing.T) {
		c := routedCargo(t)
		require.NoError(t, c.AssignToRouting())
		require.NoError(t, c.AssignItinerary(it))
		require.Equal(t, shared.RoutingStatusRouted, c.RoutingStatus())
		require.NoError(t, c.MarkMisrouted())
		assert.Equal(t, shared.RoutingStatusMisrouted, c.RoutingStatus())
		// BookingStatus は不変
		assert.Equal(t, domain.BookingStatusRouteProposed, c.Status())
	})
	t.Run("NOT_ROUTED は MISROUTED にできない", func(t *testing.T) {
		c := routedCargo(t)
		require.NoError(t, c.AssignToRouting())
		err := c.MarkMisrouted()
		require.ErrorIs(t, err, domain.ErrInvalidStatusTransition)
	})
	t.Run("MISROUTED 後に再割り当てで ROUTED に戻せる", func(t *testing.T) {
		c := routedCargo(t)
		require.NoError(t, c.AssignToRouting())
		require.NoError(t, c.AssignItinerary(it))
		require.NoError(t, c.MarkMisrouted())
		require.NoError(t, c.AssignItinerary(it)) // BookingStatus は ROUTE_PROPOSED のままなので再割り当て可
		assert.Equal(t, shared.RoutingStatusRouted, c.RoutingStatus())
	})
}
