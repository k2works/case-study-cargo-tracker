# frozen_string_literal: true

module Booking
  module Application
    # 経路（旅程）を予約に紐付けるユースケース（US09/US11）。
    # 選択した経路候補の脚データ（プリミティブ Hash）から CargoItinerary を組み立て、
    # 悲観ロック下で ROUTE_REQUESTED → ROUTE_PROPOSED へ遷移させる。
    class AssignItinerary
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new)
        @repository = repository
      end

      # legs: [{ load_location:, unload_location:, voyage_number:, load_time:, unload_time: }] の配列
      def call(booking_id_value:, legs:)
        itinerary = build_itinerary(legs)
        booking_id = Domain::BookingId.new(value: booking_id_value)
        cargo = @repository.with_locked_cargo(booking_id) { |c| c.assign_itinerary(itinerary) }
        return Result.new(status: :not_found) if cargo.nil?

        DomainEvents.publish("cargo_routed", cargo_routed_payload(cargo))
        Result.new(status: :ok)
      rescue Domain::Cargo::InvalidItineraryError => e
        Result.new(status: :invalid, error_message: e.message)
      rescue Domain::BookingStatus::InvalidTransition => e
        Result.new(status: :invalid, error_message: e.message)
      rescue ArgumentError => e
        Result.new(status: :invalid, error_message: e.message)
      end

      private

      def build_itinerary(legs)
        raise ArgumentError, "経路（脚）が指定されていません" if legs.blank?

        domain_legs = legs.map do |l|
          Domain::Leg.new(
            load_location: l[:load_location], unload_location: l[:unload_location],
            voyage_number: l[:voyage_number], load_time: l[:load_time], unload_time: l[:unload_time]
          )
        end
        Domain::CargoItinerary.new(legs: domain_legs)
      end

      def cargo_routed_payload(cargo)
        {
          cargo_id: cargo.booking_id.value, shipper_id: cargo.shipper_id,
          origin: cargo.cargo_itinerary.origin, destination: cargo.cargo_itinerary.destination,
          expected_arrival_time: cargo.cargo_itinerary.expected_arrival_time
        }
      end
    end
  end
end
