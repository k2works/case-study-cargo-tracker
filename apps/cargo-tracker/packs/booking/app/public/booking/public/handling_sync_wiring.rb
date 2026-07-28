# frozen_string_literal: true

module Booking
  module Public
    # 荷役イベントを予約へ反映する購読結線口（US15/US16・ADR-0002）。
    # 合成ルート（初期化子）から呼び出す。
    module HandlingSyncWiring
      module_function

      def install!(booking_service: CargoBookingService.new)
        DomainEvents.install_once(:booking_handling_sync) do
          DomainEvents.subscribe("handling_activity_registered") do |payload|
            booking_service.sync_handling_event(
              payload[:booking_id], type: payload[:event_type],
              location: payload[:location], voyage: payload[:voyage_number]
            )
          end
        end
      end
    end
  end
end
