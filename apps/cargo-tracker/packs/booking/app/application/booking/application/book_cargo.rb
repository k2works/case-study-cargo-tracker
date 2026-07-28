# frozen_string_literal: true

module Booking
  module Application
    # 貨物予約登録ユースケース（US04/US05 / BookCargoCommand）。
    # 荷主存在確認（ACL）→ 集約生成 → 永続化。
    class BookCargo
      Result = Struct.new(:booking_id, :error_message, keyword_init: true) do
        def success?
          booking_id.present? && error_message.nil?
        end
      end

      def initialize(repository:, shipper_existence_checker:, notifier: Infrastructure::LogRoutingNotifier.new)
        @repository = repository
        @checker = shipper_existence_checker
        @notifier = notifier
      end

      def call(shipper_id:, cargo_type:, weight_kg:, origin:, destination:, arrival_deadline:,
               dimensions: nil, quantity: nil, description: nil,
               hazardous_declaration: nil, temperature_requirement: nil)
        return Result.new(error_message: "指定された荷主が存在しません") unless @checker.exists?(shipper_id)

        cargo = Domain::Cargo.book(
          shipper_id: shipper_id,
          cargo_type: Domain::CargoType.new(value: cargo_type),
          weight_kg: BigDecimal(weight_kg.to_s),
          route_specification: Domain::RouteSpecification.new(
            origin: origin, destination: destination, arrival_deadline: Date.parse(arrival_deadline.to_s)
          ),
          dimensions: dimensions, quantity: quantity, description: description,
          hazardous_declaration: hazardous_declaration, temperature_requirement: temperature_requirement
        )
        @repository.save(cargo)
        @notifier.notify_booking_registered(cargo.booking_id.value) # US04 経路設計者へ通知
        Result.new(booking_id: cargo.booking_id.value)
      rescue ArgumentError => e
        Result.new(error_message: e.message)
      end
    end
  end
end
