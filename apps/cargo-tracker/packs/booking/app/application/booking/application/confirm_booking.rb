# frozen_string_literal: true

module Booking
  module Application
    # 予約確定ユースケース（US13）。ROUTE_PROPOSED → CONFIRMED。
    # 確定後、経路設計者へ追跡番号発行依頼のドメインイベントを発行する（ADR-0002）。
    class ConfirmBooking
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new)
        @repository = repository
      end

      def call(booking_id_value:)
        booking_id = Domain::BookingId.new(value: booking_id_value)
        cargo = @repository.with_locked_cargo(booking_id, &:confirm)
        return Result.new(status: :not_found) if cargo.nil?

        DomainEvents.publish("cargo_confirmed", { cargo_id: cargo.booking_id.value })
        Result.new(status: :ok)
      rescue Domain::BookingStatus::InvalidTransition => e
        Result.new(status: :invalid, error_message: e.message)
      rescue ArgumentError
        Result.new(status: :not_found)
      end
    end
  end
end
