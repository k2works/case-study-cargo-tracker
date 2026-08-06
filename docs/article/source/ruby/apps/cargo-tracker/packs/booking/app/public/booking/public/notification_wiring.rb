# frozen_string_literal: true

module Booking
  module Public
    # ドメインイベント購読ハンドラの公開結線口（ADR-0002）。
    # 合成ルート（初期化子）から呼び出し、内部の購読ハンドラを登録する。
    module NotificationWiring
      module_function

      def install!
        # 冪等ガード（T26）: 多重購読・多重発行を防ぐ。reset! 後は再結線される。
        DomainEvents.install_once(:booking_notifications) do
          Application::NotificationSubscribers.install!
        end
      end
    end
  end
end
