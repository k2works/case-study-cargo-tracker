package application

import (
	"context"

	"github.com/k2works/case-study-cargo-tracker/apps/cargo-tracker/internal/tracking/domain"
)

// TrackingEventView は追跡イベント 1 件の表示モデル。
type TrackingEventView struct {
	TypeJa         string
	TypeCode       string
	Location       string
	VoyageNumber   string
	CompletionTime string
	StatusJa       string
}

// TrackingView は追跡照会（US18）の表示モデル（CQRS 読み取り）。
type TrackingView struct {
	TrackingNumber  string
	BookingID       string
	StatusJa        string
	StatusCode      string
	CurrentLocation string
	Events          []TrackingEventView
}

// TrackingQueryService は追跡情報照会ユースケース（読み取り最適化）。
type TrackingQueryService struct {
	repo TrackingActivityRepository
}

// NewTrackingQueryService は TrackingQueryService を生成する。
func NewTrackingQueryService(repo TrackingActivityRepository) *TrackingQueryService {
	return &TrackingQueryService{repo: repo}
}

// FindByTrackingNumber は追跡番号から追跡情報の表示モデルを返す（US18）。
func (s *TrackingQueryService) FindByTrackingNumber(ctx context.Context, trackingNumber string) (TrackingView, error) {
	activity, err := s.repo.FindByTrackingNumber(ctx, trackingNumber)
	if err != nil {
		return TrackingView{}, err
	}
	return toView(activity), nil
}

func toView(a *domain.TrackingActivity) TrackingView {
	status := a.CurrentStatus()
	events := a.Events()
	views := make([]TrackingEventView, 0, len(events))
	for _, e := range events {
		views = append(views, TrackingEventView{
			TypeJa:         handlingTypeJa(e.EventType()),
			TypeCode:       e.EventType(),
			Location:       e.Location().UnLocode(),
			VoyageNumber:   e.VoyageNumber(),
			CompletionTime: e.CompletionTime().Format("2006-01-02 15:04"),
			StatusJa:       e.TransportStatus().Ja(),
		})
	}
	return TrackingView{
		TrackingNumber:  a.TrackingNumber().Value(),
		BookingID:       a.BookingId(),
		StatusJa:        status.Ja(),
		StatusCode:      string(status),
		CurrentLocation: a.CurrentLocation().UnLocode(),
		Events:          views,
	}
}

// handlingTypeJa は荷役種別コードの日本語表示を返す（表示専用）。
func handlingTypeJa(code string) string {
	switch code {
	case "RECEIVE":
		return "受領"
	case "LOAD":
		return "積込"
	case "UNLOAD":
		return "荷降し"
	case "CUSTOMS":
		return "通関"
	case "CLAIM":
		return "引取"
	default:
		return code
	}
}
