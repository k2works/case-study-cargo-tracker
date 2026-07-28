# frozen_string_literal: true

module Tracking
  module Application
    # 追跡番号発行ユースケース（US14）。確定済み予約に一意の追跡番号を発行する。
    # Booking Context へは公開 API（CargoBookingService）経由でのみアクセスする（ADR-0001/0003）。
    # 発行後、荷主へ追跡番号を通知するドメインイベント tracking_number_issued を発行する（ADR-0002）。
    class AssignTrackingNumber
      Result = Struct.new(:status, :tracking_number, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordTrackingRepository.new,
                     booking_service: Booking::Public::CargoBookingService.new)
        @repository = repository
        @booking_service = booking_service
      end

      def call(booking_id_value:)
        booking = @booking_service.find(booking_id_value)
        return Result.new(status: :not_found) if booking.nil?
        return Result.new(status: :already_issued, tracking_number: booking.tracking_number) if @repository.exists_for_booking?(booking_id_value)

        activity = Domain::TrackingActivity.issue(booking_id: booking_id_value)

        # Booking 側の状態遷移（CONFIRMED→TRACKING_ISSUED）が成立して初めて追跡活動を永続化する。
        booking_status = @booking_service.issue_tracking_number(booking_id_value, activity.tracking_number.value)
        return Result.new(status: booking_status) unless booking_status == :ok

        @repository.save(activity)
        DomainEvents.publish("tracking_number_issued", {
          cargo_id: booking_id_value, shipper_id: booking.shipper_id,
          tracking_number: activity.tracking_number.value
        })
        Result.new(status: :ok, tracking_number: activity.tracking_number.value)
      end
    end
  end
end
