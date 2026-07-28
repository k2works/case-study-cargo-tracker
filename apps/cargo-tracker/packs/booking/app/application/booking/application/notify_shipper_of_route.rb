# frozen_string_literal: true

module Booking
  module Application
    # 確定経路を荷主へ通知するユースケース（US12）。営業担当者の明示操作で発火する。
    # ドメインイベント cargo_routed を発行し、購読ハンドラが荷主への経路通知を記録する（ADR-0002）。
    class NotifyShipperOfRoute
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new)
        @repository = repository
      end

      def call(booking_id_value:)
        cargo = load(booking_id_value)
        return Result.new(status: :not_found) if cargo.nil?
        return Result.new(status: :invalid, error_message: "経路が紐付いていません") if cargo.cargo_itinerary.nil?

        DomainEvents.publish("cargo_routed", payload(cargo))
        Result.new(status: :ok)
      end

      private

      def load(booking_id_value)
        @repository.find_by_booking_id(Domain::BookingId.new(value: booking_id_value))
      rescue ArgumentError
        nil
      end

      def payload(cargo)
        {
          cargo_id: cargo.booking_id.value, shipper_id: cargo.shipper_id,
          origin: cargo.cargo_itinerary.origin, destination: cargo.cargo_itinerary.destination,
          expected_arrival_time: cargo.cargo_itinerary.expected_arrival_time
        }
      end
    end
  end
end
