# frozen_string_literal: true

# ドメインイベントの発行・購読基盤（ADR-0002）。
# ActiveSupport::Notifications をラップした同期購読。イベント名は domain_event.<snake_case>。
# ペイロードはプリミティブ Hash のみ（BC 間の ACL 役割）。
module DomainEvents
  CHANNEL_PREFIX = "domain_event"

  module_function

  def publish(event_name, payload)
    ActiveSupport::Notifications.instrument("#{CHANNEL_PREFIX}.#{event_name}", payload)
  end

  def subscribe(event_name, &handler)
    ActiveSupport::Notifications.subscribe("#{CHANNEL_PREFIX}.#{event_name}") do |*args|
      payload = ActiveSupport::Notifications::Event.new(*args).payload
      begin
        handler.call(payload)
      rescue StandardError => e
        # 購読側例外は発行側トランザクションへ伝播させない（ADR-0002）。
        Rails.logger.error("[DomainEvents] #{event_name} ハンドラ失敗: #{e.message}")
      end
    end
  end

  # テスト用途: 全購読解除。
  def reset!
    ActiveSupport::Notifications.notifier = ActiveSupport::Notifications::Fanout.new
  end
end
