# frozen_string_literal: true

module Routing
  module Infrastructure
    # 航海リポジトリの Active Record 実装（出力アダプタ）。
    # Voyage 集約（PORO）と VoyageRecord/CarrierMovementRecord（AR）の相互変換を担う。
    class ActiveRecordVoyageRepository < Domain::VoyageRepository
      def save(voyage)
        VoyageRecord.transaction do
          record = VoyageRecord.find_or_initialize_by(voyage_number: voyage.voyage_number.value)
          record.assign_attributes(
            carrier_name: voyage.carrier_name,
            ship_name: voyage.ship_name,
            supported_cargo_types: voyage.supported_cargo_types.join(",")
          )
          record.save!
          replace_movements(record, voyage.schedule.movements)
        end
        voyage
      end

      def find_by_number(voyage_number)
        record = VoyageRecord.includes(:carrier_movement_records).find_by(voyage_number: voyage_number.value)
        record && to_domain(record)
      end

      def exists_by_number?(number)
        VoyageRecord.exists?(voyage_number: number)
      end

      def all
        VoyageRecord.includes(:carrier_movement_records).order(:voyage_number).map { |r| to_domain(r) }
      end

      private

      def replace_movements(record, movements)
        record.carrier_movement_records.delete_all
        movements.each do |m|
          record.carrier_movement_records.create!(
            departure_location_unlocode: m.departure_unlocode,
            arrival_location_unlocode: m.arrival_unlocode,
            departure_date: m.departure_date, arrival_date: m.arrival_date,
            seq_number: m.seq_number
          )
        end
      end

      def to_domain(record)
        movements = record.carrier_movement_records.sort_by(&:seq_number).map do |m|
          Domain::CarrierMovement.new(
            departure_unlocode: m.departure_location_unlocode,
            arrival_unlocode: m.arrival_location_unlocode,
            departure_date: m.departure_date, arrival_date: m.arrival_date,
            seq_number: m.seq_number
          )
        end
        Domain::Voyage.reconstitute(
          voyage_number: record.voyage_number, carrier_name: record.carrier_name,
          ship_name: record.ship_name, supported_cargo_types: record.supported_cargo_types.to_s.split(","),
          movements: movements
        )
      end
    end
  end
end
