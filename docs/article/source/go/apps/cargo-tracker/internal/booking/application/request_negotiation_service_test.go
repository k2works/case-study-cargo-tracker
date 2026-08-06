package application_test

import (
	"context"
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/booking/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type negotiationPublisher struct {
	name    string
	payload any
	err     error
}

func (s *negotiationPublisher) Publish(_ context.Context, name string, payload any) error {
	s.name, s.payload = name, payload
	return s.err
}

func TestRequestNegotiationService(t *testing.T) {
	t.Run("条件協議依頼イベントを発行する", func(t *testing.T) {
		repo := &stubItineraryRepo{cargo: proposedCargo(t)}
		pub := &negotiationPublisher{}
		svc := application.NewRequestNegotiationService(repo, pub)
		id, _ := domain.NewBookingId("BINT-0001")

		require.NoError(t, svc.Request(context.Background(), id, "期限延長でも候補なし"))
		assert.Equal(t, "RouteNegotiationRequested", pub.name)
		req, ok := pub.payload.(application.NegotiationRequest)
		require.True(t, ok)
		assert.Equal(t, "BINT-0001", req.BookingID)
		assert.Equal(t, "JPTYO", req.Origin)
		assert.Equal(t, "USLAX", req.Destination)
		assert.Equal(t, "期限延長でも候補なし", req.Reason)
	})
	t.Run("予約が存在しなければエラー", func(t *testing.T) {
		repo := &stubItineraryRepo{}
		pub := &negotiationPublisher{}
		svc := application.NewRequestNegotiationService(repo, pub)
		id, _ := domain.NewBookingId("BINT-9999")
		err := svc.Request(context.Background(), id, "x")
		require.ErrorIs(t, err, domain.ErrCargoNotFound)
	})
}
