# frozen_string_literal: true

module Tracking
  module Application
    # 貨物状態手動更新ユースケース（US17）。追跡管理者が荷役記録では捕捉できない
    # 状態変化（出港・入港等）を追跡情報へ反映する。追跡イベント履歴を追加し、荷主へ通知する（ADR-0002）。
    class UpdateTrackingStatusManually
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordTrackingRepository.new,
                     booking_service: Booking::Public::CargoBookingService.new)
        @repository = repository
        @booking_service = booking_service
      end

      def call(tracking_number:, transport_status:, location: nil, event_time: nil)
        activity = @repository.find_by_tracking_number(tracking_number)
        return Result.new(status: :not_found) if activity.nil?

        activity.update_status(Domain::TrackingStatus.new(value: transport_status))
        @repository.append_event(activity, event_type: "MANUAL_UPDATE",
                                 event_time: event_time || Time.current, location: location)

        booking = @booking_service.find(activity.booking_id)
        DomainEvents.publish("tracking_status_updated", {
          booking_id: activity.booking_id, shipper_id: booking&.shipper_id,
          transport_status: transport_status, location: location
        })
        Result.new(status: :ok)
      rescue ArgumentError => e
        Result.new(status: :invalid, error_message: e.message)
      end
    end
  end
end
