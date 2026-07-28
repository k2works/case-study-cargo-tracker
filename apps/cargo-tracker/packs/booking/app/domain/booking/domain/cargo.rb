# frozen_string_literal: true

module Booking
  module Domain
    # 貨物予約集約ルート（PORO）。予約の登録・状態遷移・不変条件を担う。
    class Cargo
      # 旅程がルート仕様を満たさない場合に送出する。
      class InvalidItineraryError < StandardError; end

      attr_reader :booking_id, :shipper_id, :cargo_type, :weight_kg, :route_specification,
                  :booking_status, :dimensions, :quantity, :description,
                  :hazardous_declaration, :temperature_requirement, :cargo_itinerary, :tracking_number

      # 新規貨物予約を生成する（US04/US05）。PRELIMINARY 状態で作成。
      def self.book(shipper_id:, cargo_type:, weight_kg:, route_specification:,
                    dimensions: nil, quantity: nil, description: nil,
                    hazardous_declaration: nil, temperature_requirement: nil,
                    booking_id: nil, booking_status: nil, cargo_itinerary: nil)
        raise ArgumentError, "荷主 ID は必須です" if shipper_id.nil?
        raise ArgumentError, "重量は 0 より大きい必要があります" if weight_kg.nil? || weight_kg.to_f <= 0

        validate_special_cargo!(cargo_type, hazardous_declaration, temperature_requirement)

        new(
          booking_id: booking_id || BookingId.generate,
          shipper_id: shipper_id, cargo_type: cargo_type, weight_kg: weight_kg,
          route_specification: route_specification,
          booking_status: booking_status || BookingStatus.initial,
          dimensions: dimensions, quantity: quantity, description: description,
          hazardous_declaration: hazardous_declaration, temperature_requirement: temperature_requirement,
          cargo_itinerary: cargo_itinerary
        )
      end

      # 永続化からの復元専用（生成時バリデーションを再評価しない・T24）。
      # 永続データは登録時に検証済みのため、リポジトリからのみ利用する。
      def self.reconstitute(booking_id:, shipper_id:, cargo_type:, weight_kg:, route_specification:,
                            booking_status:, dimensions: nil, quantity: nil, description: nil,
                            hazardous_declaration: nil, temperature_requirement: nil, cargo_itinerary: nil,
                            tracking_number: nil)
        new(
          booking_id: booking_id, shipper_id: shipper_id, cargo_type: cargo_type, weight_kg: weight_kg,
          route_specification: route_specification, booking_status: booking_status,
          dimensions: dimensions, quantity: quantity, description: description,
          hazardous_declaration: hazardous_declaration, temperature_requirement: temperature_requirement,
          cargo_itinerary: cargo_itinerary, tracking_number: tracking_number
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
                     hazardous_declaration: nil, temperature_requirement: nil, cargo_itinerary: nil,
                     tracking_number: nil)
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
        @cargo_itinerary = cargo_itinerary
        @tracking_number = tracking_number
      end

      # 追跡番号を発行する（US14）。CONFIRMED → TRACKING_ISSUED。追跡番号を保持する。
      def issue_tracking_number(tracking_number)
        raise ArgumentError, "追跡番号は必須です" if tracking_number.nil? || tracking_number.to_s.strip.empty?

        @booking_status = booking_status.transition_to(BookingStatus::TRACKING_ISSUED)
        @tracking_number = tracking_number
      end

      # 経路設計者へ引き渡す（US06）。PRELIMINARY → ROUTE_REQUESTED。
      def assign_to_routing
        @booking_status = booking_status.transition_to(BookingStatus::ROUTE_REQUESTED)
      end

      # 旅程を紐付ける（US09/US11）。ROUTE_REQUESTED → ROUTE_PROPOSED。
      # ルート仕様（出発地・目的地・期限）を満たさない旅程は拒否する。
      def assign_itinerary(itinerary)
        unless route_specification.satisfied_by?(itinerary)
          raise InvalidItineraryError, "旅程がルート仕様を満たしません"
        end

        @booking_status = booking_status.transition_to(BookingStatus::ROUTE_PROPOSED)
        @cargo_itinerary = itinerary
      end

      # 予約を確定する（US13）。ROUTE_PROPOSED → CONFIRMED。
      def confirm
        @booking_status = booking_status.transition_to(BookingStatus::CONFIRMED)
      end

      # ルート変更のため経路設計中へ差し戻す（US13）。ROUTE_PROPOSED → ROUTE_REQUESTED。
      # 旅程は破棄する（再設計するため）。
      def back_to_routing
        @booking_status = booking_status.transition_to(BookingStatus::ROUTE_REQUESTED)
        @cargo_itinerary = nil
      end

      # 予約をキャンセルする（US13）。任意の取消可能状態 → CANCELLED。
      def cancel
        @booking_status = booking_status.transition_to(BookingStatus::CANCELLED)
      end
    end
  end
end
