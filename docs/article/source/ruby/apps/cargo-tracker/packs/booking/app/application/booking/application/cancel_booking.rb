# frozen_string_literal: true

module Booking
  module Application
    # 予約キャンセルユースケース（US13）。取消可能状態 → CANCELLED。
    # キャンセル後、荷主へキャンセル確認のドメインイベントを発行する（ADR-0002）。
    class CancelBooking
      Result = Struct.new(:status, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordCargoRepository.new)
        @repository = repository
      end

      def call(booking_id_value:)
        booking_id = Domain::BookingId.new(value: booking_id_value)
        cargo = @repository.with_locked_cargo(booking_id, &:cancel)
        return Result.new(status: :not_found) if cargo.nil?

        DomainEvents.publish("cargo_cancelled",
                             { cargo_id: cargo.booking_id.value, shipper_id: cargo.shipper_id })
        Result.new(status: :ok)
      rescue Domain::BookingStatus::InvalidTransition => e
        Result.new(status: :invalid, error_message: e.message)
      rescue ArgumentError
        Result.new(status: :not_found)
      end
    end
  end
end
