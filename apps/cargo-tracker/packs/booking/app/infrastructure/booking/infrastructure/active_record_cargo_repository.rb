# frozen_string_literal: true

module Booking
  module Infrastructure
    # 貨物予約リポジトリの Active Record 実装（出力アダプタ）。
    # Cargo 集約（PORO）と CargoRecord（AR）の相互変換を担う。
    class ActiveRecordCargoRepository < Domain::CargoRepository
      def save(cargo)
        record = CargoRecord.find_or_initialize_by(booking_id: cargo.booking_id.value)
        record.assign_attributes(to_columns(cargo))
        record.save!
        cargo
      end

      def find_by_booking_id(booking_id)
        record = CargoRecord.find_by(booking_id: booking_id.value)
        record && to_domain(record)
      end

      def all
        CargoRecord.order(:created_at).map { |r| to_domain(r) }
      end

      private

      def to_columns(cargo)
        columns = {
          booking_id: cargo.booking_id.value,
          shipper_id: cargo.shipper_id,
          cargo_type: cargo.cargo_type.value,
          weight_kg: cargo.weight_kg,
          origin_unlocode: cargo.route_specification.origin,
          destination_unlocode: cargo.route_specification.destination,
          arrival_deadline: cargo.route_specification.arrival_deadline,
          booking_status: cargo.booking_status.value.downcase,
          quantity: cargo.quantity,
          description: cargo.description
        }
        columns.merge!(dimension_columns(cargo.dimensions))
        columns.merge!(hazardous_columns(cargo.hazardous_declaration))
        columns.merge!(temperature_columns(cargo.temperature_requirement))
        columns
      end

      def dimension_columns(dimensions)
        return { dimension_length: nil, dimension_width: nil, dimension_height: nil } if dimensions.nil?

        { dimension_length: dimensions.length, dimension_width: dimensions.width,
          dimension_height: dimensions.height }
      end

      def hazardous_columns(declaration)
        return { hazardous_class: nil, un_number: nil, proper_shipping_name: nil } if declaration.nil?

        { hazardous_class: declaration.hazardous_class, un_number: declaration.un_number,
          proper_shipping_name: declaration.proper_shipping_name }
      end

      def temperature_columns(requirement)
        return { min_temperature: nil, max_temperature: nil, temperature_unit: nil } if requirement.nil?

        { min_temperature: requirement.min_temperature, max_temperature: requirement.max_temperature,
          temperature_unit: requirement.unit }
      end

      def to_domain(record)
        Domain::Cargo.book(
          booking_id: Domain::BookingId.new(value: record.booking_id),
          shipper_id: record.shipper_id,
          cargo_type: Domain::CargoType.new(value: record.cargo_type),
          weight_kg: record.weight_kg,
          route_specification: Domain::RouteSpecification.new(
            origin: record.origin_unlocode, destination: record.destination_unlocode,
            arrival_deadline: record.arrival_deadline
          ),
          booking_status: Domain::BookingStatus.new(value: record.booking_status.upcase),
          dimensions: dimensions_of(record),
          quantity: record.quantity,
          description: record.description,
          hazardous_declaration: hazardous_of(record),
          temperature_requirement: temperature_of(record)
        )
      end

      def dimensions_of(record)
        return nil if record.dimension_length.nil? && record.dimension_width.nil? && record.dimension_height.nil?

        Domain::Dimensions.new(length: record.dimension_length, width: record.dimension_width,
                               height: record.dimension_height)
      end

      def hazardous_of(record)
        return nil if record.hazardous_class.blank?

        Domain::HazardousDeclaration.new(hazardous_class: record.hazardous_class,
                                         un_number: record.un_number,
                                         proper_shipping_name: record.proper_shipping_name)
      end

      def temperature_of(record)
        return nil if record.temperature_unit.blank?

        Domain::TemperatureRequirement.new(min_temperature: record.min_temperature,
                                           max_temperature: record.max_temperature,
                                           unit: record.temperature_unit)
      end
    end
  end
end
