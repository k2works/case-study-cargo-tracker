# frozen_string_literal: true

module Handling
  module Application
    # 荷役作業記録ユースケース（US15/US16）。追跡番号で貨物を特定し、荷役作業を記録する。
    # Booking Context へは公開 API（CargoBookingService）経由のみ（ACL・ADR-0001/0003）。
    # 記録後 handling_activity_registered イベントを発行し、追跡状態同期・予約状態同期・通知を駆動する（ADR-0002）。
    class RegisterHandlingActivity
      Result = Struct.new(:status, :route_check, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordHandlingRepository.new,
                     booking_service: Booking::Public::CargoBookingService.new)
        @repository = repository
        @booking_service = booking_service
      end

      # recipient: { name:, signature:, confirmation_code: }（引取 CLAIM 時のみ利用）
      def call(tracking_number:, event_type:, location:, completion_time:,
               voyage_number: nil, operator_name: nil, recipient: {})
        booking = @booking_service.find_by_tracking_number(tracking_number)
        return Result.new(status: :not_found, error_message: "追跡番号が存在しません") if booking.nil?

        # 作業種別の前提状態を検証し、Booking/Tracking の状態機械が乖離するのを防ぐ（例: 積込前の引取）。
        precondition = precondition_error(event_type, booking)
        return Result.new(status: :invalid, error_message: precondition) if precondition

        # 冪等ガード（T28）: 同一荷役の二重 POST を検知し、二重記録・二重通知を防ぐ。
        if @repository.duplicate?(booking_id: booking.booking_id, event_type: event_type,
                                  completion_time: completion_time, voyage_number: voyage_number)
          return Result.new(status: :duplicate, error_message: "同一の荷役作業が既に記録されています")
        end

        activity = Domain::HandlingActivity.register(
          booking_id: booking.booking_id, type: Domain::HandlingType.new(value: event_type),
          location: location, completion_time: completion_time, voyage_number: voyage_number,
          operator_name: operator_name,
          recipient_confirmation: recipient_confirmation(event_type, recipient || {})
        )
        route = activity.route_check(snapshot_of(booking))
        @repository.save(activity)

        DomainEvents.publish("handling_activity_registered", {
          booking_id: booking.booking_id, tracking_number: tracking_number, shipper_id: booking.shipper_id,
          event_type: event_type, location: location, voyage_number: voyage_number,
          completion_time: completion_time, route_check: route
        })
        Result.new(status: :ok, route_check: route)
      rescue ArgumentError => e
        Result.new(status: :invalid, error_message: e.message)
      end

      private

      # 荷役種別ごとの前提となる予約状態を検証する（該当なしは nil）。
      # 追跡番号発行済み（TRACKING_ISSUED 以降）が最低条件。引取（CLAIM）は積込済み（IN_TRANSIT）が前提。
      def precondition_error(event_type, booking)
        unless booking.status_value.in?(%w[TRACKING_ISSUED IN_TRANSIT DELIVERED])
          return "追跡番号発行後の予約のみ荷役を記録できます"
        end
        if event_type == Domain::HandlingType::CLAIM && booking.status_value != "IN_TRANSIT"
          return "引取は積込済み（輸送中）の貨物のみ記録できます"
        end

        nil
      end

      def recipient_confirmation(event_type, recipient)
        return nil unless event_type == Domain::HandlingType::CLAIM

        Domain::RecipientConfirmation.new(
          recipient_name: recipient[:name], signature: recipient[:signature],
          confirmation_code: recipient[:confirmation_code]
        )
      end

      def snapshot_of(booking)
        leg_locations = booking.itinerary_legs.flat_map { |l| [ l.load_location, l.unload_location ] }.uniq
        Domain::CargoSnapshot.new(
          booking_id: booking.booking_id, origin: booking.origin, destination: booking.destination,
          leg_locations: leg_locations
        )
      end
    end
  end
end
