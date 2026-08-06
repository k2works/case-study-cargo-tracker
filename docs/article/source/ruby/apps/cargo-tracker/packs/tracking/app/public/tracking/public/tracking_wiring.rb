# frozen_string_literal: true

module Tracking
  module Public
    # Tracking Context の購読結線口（ADR-0002）。合成ルート（初期化子）から呼び出す。
    module TrackingWiring
      module_function

      def install!
        DomainEvents.install_once(:tracking_handling_sync) do
          handler = Application::HandlingEventHandler.new
          DomainEvents.subscribe("handling_activity_registered") { |payload| handler.call(payload) }
        end
      end
    end
  end
end
