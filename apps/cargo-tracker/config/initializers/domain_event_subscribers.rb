# frozen_string_literal: true

# ドメインイベント購読ハンドラの登録（ADR-0002）。
# 集約が発行したイベント（cargo_routed / cargo_confirmed / cargo_cancelled）を
# 通知記録ハンドラへ結線する。eager load 後に一度だけ登録する。
Rails.application.config.after_initialize do
  Booking::Public::NotificationWiring.install!
end
