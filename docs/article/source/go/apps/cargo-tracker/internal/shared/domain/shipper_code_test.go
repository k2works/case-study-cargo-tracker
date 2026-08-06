package domain_test

import (
	"testing"

	shared "github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/shared/domain"
)

func TestNewShipperCode(t *testing.T) {
	t.Run("値を持つ ShipperCode を生成できる", func(t *testing.T) {
		code, err := shared.NewShipperCode("SHP-ABCDEF12")
		if err != nil {
			t.Fatalf("予期しないエラー: %v", err)
		}
		if code.Value() != "SHP-ABCDEF12" {
			t.Errorf("want SHP-ABCDEF12, got %s", code.Value())
		}
	})

	t.Run("空文字の ShipperCode はエラーになる", func(t *testing.T) {
		if _, err := shared.NewShipperCode(""); err == nil {
			t.Error("空文字はエラーになるべき")
		}
	})

	t.Run("同じ値の ShipperCode は等価である", func(t *testing.T) {
		a, _ := shared.NewShipperCode("SHP-0001")
		b, _ := shared.NewShipperCode("SHP-0001")
		if a != b {
			t.Error("同じ値の ShipperCode は等価であるべき")
		}
	})
}
