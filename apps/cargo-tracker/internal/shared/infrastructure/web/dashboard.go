package web

import (
	"context"
	"net/http"
)

// DashboardSummary はダッシュボードのサマリーカードに表示する集計値。
// 各 BC のクエリサービスを合成ルート（cmd）で束ねた SummaryProvider が算出する。
type DashboardSummary struct {
	Bookings       int // 貨物予約数
	Shippers       int // 荷主数
	Estimates      int // 見積数
	Voyages        int // 航路数
	Invoices       int // 請求書数
	UnpaidInvoices int // 未入金請求書数
}

// SummaryProvider はダッシュボードの集計値を提供する入力ポート。
// 実装は合成ルート（cmd/server）で各 BC のクエリサービスを横断的に束ねる。
type SummaryProvider interface {
	Summarize(ctx context.Context) DashboardSummary
}

// NewDashboardHandler はダッシュボード画面（全ロール共通）のハンドラを生成する。
// ロール別のカード表示制御はテンプレート側で CurrentUser.CanAccess により行う（navbar と一貫）。
func NewDashboardHandler(renderer *Renderer, provider SummaryProvider) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		summary := provider.Summarize(r.Context())
		renderer.RenderPage(w, r, "templates/dashboard.html", summary)
	}
}
