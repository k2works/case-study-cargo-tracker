package domain_test

import (
	"testing"

	handling "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/handling/domain"
	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseHandlingType(t *testing.T) {
	for _, v := range []string{"RECEIVE", "LOAD", "UNLOAD", "CUSTOMS", "CLAIM"} {
		ht, err := handling.ParseHandlingType(v)
		require.NoError(t, err)
		assert.Equal(t, v, ht.String())
	}
	_, err := handling.ParseHandlingType("CUSTOMS_CLEARANCE")
	assert.ErrorIs(t, err, handling.ErrUnknownHandlingType)
}

func TestHandlingTypeRequiresVoyageNumber(t *testing.T) {
	assert.True(t, handling.HandlingTypeLoad.RequiresVoyageNumber())
	assert.True(t, handling.HandlingTypeUnload.RequiresVoyageNumber())
	assert.False(t, handling.HandlingTypeReceive.RequiresVoyageNumber())
	assert.False(t, handling.HandlingTypeCustoms.RequiresVoyageNumber())
	assert.False(t, handling.HandlingTypeClaim.RequiresVoyageNumber())
}

func TestHandlingTypeIsClaimType(t *testing.T) {
	assert.True(t, handling.HandlingTypeClaim.IsClaimType())
	assert.False(t, handling.HandlingTypeLoad.IsClaimType())
}

func TestHandlingTypeResultingTransportStatus(t *testing.T) {
	assert.Equal(t, shared.TransportStatusReceived, handling.HandlingTypeReceive.ResultingTransportStatus())
	assert.Equal(t, shared.TransportStatusLoaded, handling.HandlingTypeLoad.ResultingTransportStatus())
	assert.Equal(t, shared.TransportStatusUnloaded, handling.HandlingTypeUnload.ResultingTransportStatus())
	assert.Equal(t, shared.TransportStatusClaimed, handling.HandlingTypeClaim.ResultingTransportStatus())
	// CUSTOMS は輸送フェーズを進めない（現状態維持のため UNKNOWN を返す）。
	assert.Equal(t, shared.TransportStatusUnknown, handling.HandlingTypeCustoms.ResultingTransportStatus())
}

func TestHandlingTypeJa(t *testing.T) {
	assert.Equal(t, "受領", handling.HandlingTypeReceive.Ja())
	assert.Equal(t, "積込", handling.HandlingTypeLoad.Ja())
	assert.Equal(t, "荷降し", handling.HandlingTypeUnload.Ja())
	assert.Equal(t, "通関", handling.HandlingTypeCustoms.Ja())
	assert.Equal(t, "引取", handling.HandlingTypeClaim.Ja())
}
