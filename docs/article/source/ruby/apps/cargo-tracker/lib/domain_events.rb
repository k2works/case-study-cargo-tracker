# frozen_string_literal: true

# ドメインイベントの発行・購読基盤（ADR-0002）。
# ActiveSupport::Notifications をラップした同期購読。イベント名は domain_event.<snake_case>。
# ペイロードはプリミティブ Hash のみ（BC 間の ACL 役割）。
module DomainEvents
  CHANNEL_PREFIX = "domain_event"

  # 結線の冪等ガード（多重購読防止・T26）。install_once のキー集合。reset! でクリアされる。
  @installed = Set.new

  module_function

  def publish(event_name, payload)
    ActiveSupport::Notifications.instrument("#{CHANNEL_PREFIX}.#{event_name}", payload)
  end

  # 同一キーの結線ブロックを一度だけ実行する（多重購読・多重発行を防ぐ・T26）。
  # reset! で購読が解除されるとガードもクリアされ、再結線できる。
  def install_once(key)
    return false if @installed.include?(key)

    yield
    @installed.add(key)
    true
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

  # テスト用途: 全購読解除。冪等ガードもクリアし再結線を可能にする。
  def reset!
    ActiveSupport::Notifications.notifier = ActiveSupport::Notifications::Fanout.new
    @installed.clear
  end
end
