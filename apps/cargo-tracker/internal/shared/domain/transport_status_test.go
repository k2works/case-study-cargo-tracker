package domain_test

import (
	"testing"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
)

func TestTransportStatusJa(t *testing.T) {
	assert.Equal(t, "受領待ち", shared.TransportStatusNotReceived.Ja())
	assert.Equal(t, "受領済", shared.TransportStatusReceived.Ja())
	assert.Equal(t, "積込済", shared.TransportStatusLoaded.Ja())
	assert.Equal(t, "輸送中", shared.TransportStatusOnboardCarrier.Ja())
	assert.Equal(t, "荷降し済", shared.TransportStatusUnloaded.Ja())
	assert.Equal(t, "引取待ち", shared.TransportStatusAwaitingClaim.Ja())
	assert.Equal(t, "引取済", shared.TransportStatusClaimed.Ja())
	assert.Equal(t, "例外", shared.TransportStatusException.Ja())
	assert.Equal(t, "不明", shared.TransportStatusUnknown.Ja())
	assert.Equal(t, "OTHER", shared.TransportStatus("OTHER").Ja())
}

func TestTransportStatusIsValid(t *testing.T) {
	assert.True(t, shared.TransportStatusNotReceived.IsValid())
	assert.True(t, shared.TransportStatusClaimed.IsValid())
	assert.False(t, shared.TransportStatus("OTHER").IsValid())
	assert.False(t, shared.TransportStatus("").IsValid())
}
