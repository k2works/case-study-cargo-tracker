package application_test

import (
	"context"
	"testing"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/application"
	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shipper/domain"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// stubRepository は ShipperRepository の手書きスタブ。
type stubRepository struct {
	saved         *domain.Shipper
	existsByEmail bool
	saveErr       error
}

func (s *stubRepository) Save(_ context.Context, shipper *domain.Shipper) error {
	if s.saveErr != nil {
		return s.saveErr
	}
	s.saved = shipper
	return nil
}

func (s *stubRepository) ExistsByEmail(_ context.Context, _ domain.Email) (bool, error) {
	return s.existsByEmail, nil
}

// stubIDGenerator は決定的な ID を返すスタブ。
type stubIDGenerator struct{ id string }

func (s stubIDGenerator) Generate() string { return s.id }

func TestRegisterShipperService_Individual(t *testing.T) {
	t.Run("個人荷主を登録し荷主 ID が発行される", func(t *testing.T) {
		repo := &stubRepository{}
		svc := application.NewRegisterShipperService(repo, stubIDGenerator{id: "abcdef12-3456-7890-abcd-ef1234567890"})

		id, err := svc.Register(context.Background(), application.RegisterShipperCommand{
			Name:        "山田太郎",
			Email:       "taro@example.com",
			ShipperType: "INDIVIDUAL",
		})

		require.NoError(t, err)
		assert.Equal(t, "abcdef12-3456-7890-abcd-ef1234567890", id.Value())
		require.NotNil(t, repo.saved)
		assert.Equal(t, "SHP-ABCDEF12", repo.saved.Code().Value())
		assert.False(t, repo.saved.IsCorporate())
	})
}

func TestRegisterShipperService_Corporate(t *testing.T) {
	t.Run("法人荷主を契約番号・割引率付きで登録できる", func(t *testing.T) {
		repo := &stubRepository{}
		svc := application.NewRegisterShipperService(repo, stubIDGenerator{id: "abcdef12-3456-7890-abcd-ef1234567890"})

		rate := 0.10
		cn := "CN-0001"
		_, err := svc.Register(context.Background(), application.RegisterShipperCommand{
			Name:           "株式会社サンプル",
			Email:          "corp@example.com",
			ShipperType:    "CORPORATE",
			ContractNumber: &cn,
			DiscountRate:   &rate,
		})

		require.NoError(t, err)
		require.NotNil(t, repo.saved)
		assert.True(t, repo.saved.IsCorporate())
		assert.Equal(t, "CN-0001", repo.saved.Contract().Number().Value())
	})
}

func TestRegisterShipperService_DuplicateEmail(t *testing.T) {
	t.Run("メール重複時は ErrEmailAlreadyRegistered を返す", func(t *testing.T) {
		repo := &stubRepository{existsByEmail: true}
		svc := application.NewRegisterShipperService(repo, stubIDGenerator{id: "x"})

		_, err := svc.Register(context.Background(), application.RegisterShipperCommand{
			Name:        "山田太郎",
			Email:       "dup@example.com",
			ShipperType: "INDIVIDUAL",
		})

		require.ErrorIs(t, err, application.ErrEmailAlreadyRegistered)
	})
}

func TestRegisterShipperService_CorporateWithoutContract(t *testing.T) {
	t.Run("法人なのに契約情報が無い場合はエラー", func(t *testing.T) {
		repo := &stubRepository{}
		svc := application.NewRegisterShipperService(repo, stubIDGenerator{id: "x"})

		_, err := svc.Register(context.Background(), application.RegisterShipperCommand{
			Name:        "株式会社サンプル",
			Email:       "corp@example.com",
			ShipperType: "CORPORATE",
		})

		require.Error(t, err)
	})
}
