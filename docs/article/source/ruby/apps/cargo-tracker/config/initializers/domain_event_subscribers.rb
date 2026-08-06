# frozen_string_literal: true

# ドメインイベント購読ハンドラの登録（ADR-0002）。
# 集約が発行したイベント（cargo_routed / cargo_confirmed / cargo_cancelled）を
# 通知記録ハンドラへ結線する。eager load 後に一度だけ登録する。
Rails.application.config.after_initialize do
  Booking::Public::NotificationWiring.install!    # 通知記録（cargo_*・tracking_number_issued・handling_*）
  Booking::Public::HandlingSyncWiring.install!    # 荷役 → 予約状態同期（US15/US16）
  Tracking::Public::TrackingWiring.install!       # 荷役 → 追跡状態同期・イベント履歴（US15）
end
