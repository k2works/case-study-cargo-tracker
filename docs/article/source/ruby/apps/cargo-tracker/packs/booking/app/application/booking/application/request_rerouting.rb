# frozen_string_literal: true

module Booking
  module Application
    # ルート変更差戻しユースケース（US13）。ROUTE_PROPOSED → ROUTE_REQUESTED。
    # 荷主のルート変更希望を受け、旅程を破棄して経路設計中へ戻す。
    class RequestRerouting
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new)
        @repository = repository
      end

      def call(booking_id_value:)
        booking_id = Domain::BookingId.new(value: booking_id_value)
        cargo = @repository.with_locked_cargo(booking_id, &:back_to_routing)
        return Result.new(status: :not_found) if cargo.nil?

        Result.new(status: :ok)
      rescue Domain::BookingStatus::InvalidTransition => e
        Result.new(status: :invalid, error_message: e.message)
      rescue ArgumentError
        Result.new(status: :not_found)
      end
    end
  end
end
