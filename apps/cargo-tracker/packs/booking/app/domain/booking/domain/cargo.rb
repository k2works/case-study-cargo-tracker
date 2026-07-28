# frozen_string_literal: true

module Booking
  module Domain
    # 貨物予約集約ルート（PORO）。予約の登録・状態遷移・不変条件を担う。
    class Cargo
      attr_reader :booking_id, :shipper_id, :cargo_type, :weight_kg, :route_specification,
                  :booking_status, :dimensions, :quantity, :description,
                  :hazardous_declaration, :temperature_requirement

      # 新規貨物予約を生成する（US04/US05）。PRELIMINARY 状態で作成。
      def self.book(shipper_id:, cargo_type:, weight_kg:, route_specification:,
                    dimensions: nil, quantity: nil, description: nil,
                    hazardous_declaration: nil, temperature_requirement: nil,
                    booking_id: nil, booking_status: nil)
        raise ArgumentError, "荷主 ID は必須です" if shipper_id.nil?
        raise ArgumentError, "重量は 0 より大きい必要があります" if weight_kg.nil? || weight_kg.to_f <= 0

        validate_special_cargo!(cargo_type, hazardous_declaration, temperature_requirement)

        new(
          booking_id: booking_id || BookingId.generate,
          shipper_id: shipper_id, cargo_type: cargo_type, weight_kg: weight_kg,
          route_specification: route_specification,
          booking_status: booking_status || BookingStatus.initial,
          dimensions: dimensions, quantity: quantity, description: description,
          hazardous_declaration: hazardous_declaration, temperature_requirement: temperature_requirement
        )
      end

      # 危険物/冷凍の条件付き必須制約（US05）。
      def self.validate_special_cargo!(cargo_type, hazardous, temperature)
        if cargo_type.hazardous? && hazardous.nil?
          raise ArgumentError, "危険物には危険物申告が必須です"
        end
        if cargo_type.refrigerated? && temperature.nil?
          raise ArgumentError, "冷凍・冷蔵貨物には温度管理条件が必須です"
        end
      end
      private_class_method :validate_special_cargo!

      def initialize(booking_id:, shipper_id:, cargo_type:, weight_kg:, route_specification:,
                     booking_status:, dimensions: nil, quantity: nil, description: nil,
                     hazardous_declaration: nil, temperature_requirement: nil)
        @booking_id = booking_id
        @shipper_id = shipper_id
        @cargo_type = cargo_type
        @weight_kg = weight_kg
        @route_specification = route_specification
        @booking_status = booking_status
        @dimensions = dimensions
        @quantity = quantity
        @description = description
        @hazardous_declaration = hazardous_declaration
        @temperature_requirement = temperature_requirement
      end

      # 経路設計者へ引き渡す（US06）。PRELIMINARY → ROUTE_REQUESTED。
      def assign_to_routing
        @booking_status = booking_status.transition_to(BookingStatus::ROUTE_REQUESTED)
      end
    end
  end
end
