# frozen_string_literal: true

module Tracking
  module Application
    # 追跡例外登録ユースケース（US19 遅延 / US20 破損・紛失）。
    # 追跡番号で貨物を特定し、例外を記録して輸送状態を EXCEPTION に遷移させる。
    # tracking_exception_detected を発行し、荷主通知・紛失時の管理職エスカレーションを結線する（ADR-0002）。
    class RegisterException
      Result = Struct.new(:status, :booking_id, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordTrackingRepository.new,
                     booking_service: Booking::Public::CargoBookingService.new)
        @repository = repository
        @booking_service = booking_service
      end

      def call(tracking_number:, exception_type:, occurred_at:, description: nil, location: nil)
        activity = @repository.find_by_tracking_number(tracking_number)
        return Result.new(status: :not_found) if activity.nil?

        event = activity.register_exception(
          exception_type: Domain::ExceptionType.new(value: exception_type),
          occurred_at: occurred_at, description: description, location_unlocode: location
        )
        @repository.save_exception(activity, event)

        booking = @booking_service.find(activity.booking_id)
        DomainEvents.publish("tracking_exception_detected", {
          booking_id: activity.booking_id, shipper_id: booking&.shipper_id,
          exception_type: exception_type, escalation_flag: event.escalation_flag,
          description: description, location: location
        })
        Result.new(status: :ok, booking_id: activity.booking_id)
      rescue ArgumentError => e
        Result.new(status: :invalid, error_message: e.message)
      end
    end
  end
end
