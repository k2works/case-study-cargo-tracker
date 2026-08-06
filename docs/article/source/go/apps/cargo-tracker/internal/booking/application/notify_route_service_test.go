package application_test

import (
	"context"
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type stubNotifier struct {
	shipperCode string
	summary     string
	err         error
}

func (s *stubNotifier) Notify(_ context.Context, sc shared.ShipperCode, summary string) error {
	s.shipperCode, s.summary = sc.Value(), summary
	return s.err
}

type stubNotificationRepo struct {
	saved []domain.Notification
}

func (s *stubNotificationRepo) Save(_ context.Context, _ domain.BookingId, n domain.Notification) error {
	s.saved = append(s.saved, n)
	return nil
}
func (s *stubNotificationRepo) ListByBookingID(_ context.Context, _ domain.BookingId) ([]domain.Notification, error) {
	return s.saved, nil
}

// routedCargoWithItinerary は確定経路（ROUTED）の Cargo を返す。
func routedCargoWithItinerary(t *testing.T) *domain.Cargo {
	t.Helper()
	c := proposedCargo(t) // ROUTE_PROPOSED・JPTYO→USLAX
	origin, _ := shared.NewLocation("JPTYO")
	sgsin, _ := shared.NewLocation("SGSIN")
	dest, _ := shared.NewLocation("USLAX")
	l1, _ := domain.NewLeg("V-LEG1", origin, sgsin, dt(2026, 10, 1), dt(2026, 10, 5))
	l2, _ := domain.NewLeg("V-LEG2", sgsin, dest, dt(2026, 10, 6), dt(2026, 10, 13))
	it, err := domain.NewCargoItinerary([]domain.Leg{l1, l2})
	require.NoError(t, err)
	require.NoError(t, c.AssignItinerary(it))
	return c
}

func TestNotifyRouteService_Preview(t *testing.T) {
	repo := &stubItineraryRepo{cargo: routedCargoWithItinerary(t)}
	svc := application.NewNotifyRouteService(repo, &stubNotifier{}, &stubNotificationRepo{}, shared.FixedClock{Fixed: dt(2026, 9, 1)})
	id, _ := domain.NewBookingId("BINT-0001")

	view, err := svc.Preview(context.Background(), id)
	require.NoError(t, err)
	assert.Equal(t, "JPTYO", view.Origin)
	assert.Equal(t, "USLAX", view.Destination)
	assert.Equal(t, []string{"SGSIN"}, view.Waypoints)
	assert.Equal(t, 12, view.TransitDays)
	assert.Equal(t, "2026-10-13", view.ArrivalDate)
	assert.Contains(t, view.Summary, "経由: SGSIN")
}

func TestNotifyRouteService_Notify(t *testing.T) {
	t.Run("確定経路を通知し記録を残す", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: routedCargoWithItinerary(t)}
		notifier := &stubNotifier{}
		nrepo := &stubNotificationRepo{}
		svc := application.NewNotifyRouteService(repo, notifier, nrepo, shared.FixedClock{Fixed: dt(2026, 9, 1)})
		id, _ := domain.NewBookingId("BINT-0001")

		require.NoError(t, svc.Notify(context.Background(), id))
		assert.Equal(t, "SH01", notifier.shipperCode)
		assert.Contains(t, notifier.summary, "確定経路")
		require.Len(t, nrepo.saved, 1)
		assert.Equal(t, dt(2026, 9, 1), nrepo.saved[0].SentAt())
		// 通知内容と記録が一致する（送信サマリ＝記録サマリ）
		assert.Equal(t, notifier.summary, nrepo.saved[0].Summary())
	})
	t.Run("再通知で記録が2件になる", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: routedCargoWithItinerary(t)}
		nrepo := &stubNotificationRepo{}
		svc := application.NewNotifyRouteService(repo, &stubNotifier{}, nrepo, shared.FixedClock{Fixed: dt(2026, 9, 1)})
		id, _ := domain.NewBookingId("BINT-0001")
		require.NoError(t, svc.Notify(context.Background(), id))
		require.NoError(t, svc.Notify(context.Background(), id))
		assert.Len(t, nrepo.saved, 2)
	})
	t.Run("経路未確定は通知できない", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)} // NOT_ROUTED
		svc := application.NewNotifyRouteService(repo, &stubNotifier{}, &stubNotificationRepo{}, shared.FixedClock{Fixed: dt(2026, 9, 1)})
		id, _ := domain.NewBookingId("BINT-0001")
		err := svc.Notify(context.Background(), id)
		require.ErrorIs(t, err, domain.ErrNotRoutedForNotification)
	})
}
