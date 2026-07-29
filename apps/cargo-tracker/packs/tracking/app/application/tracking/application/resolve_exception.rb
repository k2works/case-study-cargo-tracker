# frozen_string_literal: true

module Tracking
  module Application
    # 追跡例外の対応報告・解決ユースケース（US19/US20 対応報告）。
    # 未解決の例外に対応内容を記録して解決し、輸送状態を発生前の状態へ復帰させる（precondition・T30）。
    # tracking_exception_resolved を発行し、荷主へ対応報告を通知する（ADR-0002）。
    class ResolveException
      Result = Struct.new(:status, :booking_id, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordTrackingRepository.new,
                     booking_service: Booking::Public::CargoBookingService.new)
        @repository = repository
        @booking_service = booking_service
      end

      def call(tracking_number:, resolution_notes:, resolved_at: nil, revised_arrival_date: nil)
        activity = @repository.find_by_tracking_number(tracking_number)
        return Result.new(status: :not_found) if activity.nil?

        event = activity.active_exception
        return Result.new(status: :invalid, error_message: "未解決の例外がありません") if event.nil?

        activity.resolve_exception(event, resolved_at: resolved_at || Time.current,
                                   resolution_notes: resolution_notes, revised_arrival_date: revised_arrival_date)
        @repository.resolve_exception(activity)

        booking = @booking_service.find(activity.booking_id)
        DomainEvents.publish("tracking_exception_resolved", {
          booking_id: activity.booking_id, shipper_id: booking&.shipper_id,
          exception_type: event.exception_type.value, resolution_notes: resolution_notes
        })
        Result.new(status: :ok, booking_id: activity.booking_id)
      end
    end
  end
end
