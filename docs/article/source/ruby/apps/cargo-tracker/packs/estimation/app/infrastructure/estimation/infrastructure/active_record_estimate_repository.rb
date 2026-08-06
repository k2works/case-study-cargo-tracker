# frozen_string_literal: true

module Estimation
  module Infrastructure
    # 見積リポジトリの Active Record 実装（出力アダプタ）。
    # Estimate 集約（PORO）と EstimateRecord/RouteCandidateRecord（AR）の相互変換を担う。
    class ActiveRecordEstimateRepository < Domain::EstimateRepository
      def save(estimate)
        EstimateRecord.transaction do
          record = EstimateRecord.find_or_initialize_by(estimate_uuid: estimate.estimate_id)
          record.assign_attributes(
            origin_unlocode: estimate.origin, destination_unlocode: estimate.destination,
            arrival_deadline: estimate.arrival_deadline, cargo_type: estimate.cargo_type,
            weight_kg: estimate.weight_kg, status: estimate.status.value
          )
          record.save!
          RouteCandidateRecord.where(estimate_id: record.id).delete_all
          estimate.candidates.each do |c|
            RouteCandidateRecord.create!(
              estimate_id: record.id, voyage_number: c.voyage_number, transit_port: c.transit_port,
              transit_days: c.transit_days, estimated_cost: c.estimated_cost, rank: c.rank
            )
          end
        end
        estimate
      end

      def find_by_estimate_id(estimate_id)
        record = EstimateRecord.find_by(estimate_uuid: estimate_id)
        record && to_domain(record)
      end

      def all
        EstimateRecord.order(created_at: :desc).map { |r| to_domain(r) }
      end

      private

      def to_domain(record)
        candidates = RouteCandidateRecord.where(estimate_id: record.id).order(:rank).map do |c|
          Domain::RouteCandidate.new(
            voyage_number: c.voyage_number, transit_port: c.transit_port,
            transit_days: c.transit_days, estimated_cost: c.estimated_cost, rank: c.rank
          )
        end
        Domain::Estimate.reconstitute(
          estimate_id: record.estimate_uuid, origin: record.origin_unlocode,
          destination: record.destination_unlocode, arrival_deadline: record.arrival_deadline,
          cargo_type: record.cargo_type, weight_kg: record.weight_kg,
          candidates: candidates, status: Domain::EstimateStatus.new(value: record.status)
        )
      end
    end
  end
end
