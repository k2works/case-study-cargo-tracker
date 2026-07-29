# frozen_string_literal: true

module Estimation
  module Application
    # 輸送見積作成ユースケース（US01）。輸送要件から見積を作成し、Routing の経路候補と
    # Billing の概算料金を付与して永続化する。Routing/Billing へは公開 API（ACL）経由。
    class CreateEstimate
      Result = Struct.new(:status, :estimate_id, :error_message, keyword_init: true) do
        def success? = status == :ok
      end

      def initialize(repository: Infrastructure::ActiveRecordEstimateRepository.new,
                     routing: Routing::Public::VoyageDirectory.new,
                     calculator: Billing::Public::FreightCalculator.new)
        @repository = repository
        @routing = routing
        @calculator = calculator
      end

      def call(origin:, destination:, arrival_deadline:, cargo_type:, weight_kg:)
        estimate = Domain::Estimate.create(
          origin: origin, destination: destination, arrival_deadline: arrival_deadline,
          cargo_type: cargo_type, weight_kg: weight_kg
        )

        routing_result = @routing.calculate_route_candidates(
          origin: origin, destination: destination, arrival_deadline: arrival_deadline
        )
        return Result.new(status: :no_route, error_message: "期限内に間に合うルートがありません") unless routing_result.success?

        estimate.replace_candidates(build_candidates(routing_result.candidates, estimate))
        @repository.save(estimate)
        Result.new(status: :ok, estimate_id: estimate.estimate_id)
      rescue ArgumentError => e
        Result.new(status: :invalid, error_message: e.message)
      end

      private

      # Routing の候補ビューを Estimation の RouteCandidate へ変換し、Billing で概算料金を算出する。
      def build_candidates(candidates, estimate)
        candidates.each_with_index.map do |c, i|
          cost = @calculator.estimate(
            distance_factor: c.transit_days, weight_kg: estimate.weight_kg, cargo_type: estimate.cargo_type
          ).total_amount
          Domain::RouteCandidate.new(
            voyage_number: Array(c.voyage_numbers).first, transit_port: transit_port_of(c),
            transit_days: c.transit_days, estimated_cost: cost, rank: i + 1
          )
        end
      end

      # 経路の中間港（出発地・目的地を除く最初の経由港）。
      def transit_port_of(candidate)
        path = Array(candidate.route_path)
        path.length > 2 ? path[1] : nil
      end
    end
  end
end
