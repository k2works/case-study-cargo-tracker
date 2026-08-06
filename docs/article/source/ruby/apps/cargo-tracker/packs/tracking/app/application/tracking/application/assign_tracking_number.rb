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

        # 冪等回復（M2）: Booking は既に TRACKING_ISSUED で追跡番号を保持しているが Tracking 未作成の
        # 宙吊り状態（前回の途中失敗）なら、保持済み番号で追跡活動を復元して整合させる。
        return recover(booking) if booking.tracking_issued? && booking.tracking_number.present?

        activity = Domain::TrackingActivity.issue(booking_id: booking_id_value)

        # Booking 側の状態遷移（CONFIRMED→TRACKING_ISSUED）が成立して初めて追跡活動を永続化する。
        booking_status = @booking_service.issue_tracking_number(booking_id_value, activity.tracking_number.value)
        return Result.new(status: booking_status) unless booking_status == :ok

        persist_and_publish(activity, booking.shipper_id)
      end

      private

      def recover(booking)
        activity = Domain::TrackingActivity.reconstitute(
          tracking_number: Domain::TrackingNumber.new(value: booking.tracking_number),
          booking_id: booking.booking_id, transport_status: Domain::TrackingStatus.initial
        )
        persist_and_publish(activity, booking.shipper_id)
      end

      def persist_and_publish(activity, shipper_id)
        @repository.save(activity)
        DomainEvents.publish("tracking_number_issued", {
          cargo_id: activity.booking_id, shipper_id: shipper_id,
          tracking_number: activity.tracking_number.value
        })
        Result.new(status: :ok, tracking_number: activity.tracking_number.value)
      end
    end
  end
end
