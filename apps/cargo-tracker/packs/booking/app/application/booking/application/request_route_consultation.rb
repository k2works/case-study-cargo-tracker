# frozen_string_literal: true

module Booking
  module Application
    # 荷主との条件協議を依頼するユースケース（US10）。
    # 条件を調整しても満たす経路がない場合に営業担当者へ協議依頼のイベントを発行する（ADR-0002）。
    class RequestRouteConsultation
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new)
        @repository = repository
      end

      def call(booking_id_value:)
        cargo = load(booking_id_value)
        return Result.new(status: :not_found) if cargo.nil?

        DomainEvents.publish("cargo_consultation_requested", {
          cargo_id: cargo.booking_id.value, shipper_id: cargo.shipper_id,
          origin: cargo.route_specification.origin, destination: cargo.route_specification.destination
        })
        Result.new(status: :ok)
      end

      private

      def load(booking_id_value)
        @repository.find_by_booking_id(Domain::BookingId.new(value: booking_id_value))
      rescue ArgumentError
        nil
      end
    end
  end
end
