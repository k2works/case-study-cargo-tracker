# frozen_string_literal: true

module Estimation
  module Public
    # 見積の公開ファサード（アプリ層＝合成ルート／他 BC・UI 向け）。
    # 内部のユースケース・リポジトリを隠蔽し、公開ビューを返す。
    class EstimationService
      CandidateView = Data.define(:voyage_number, :transit_port, :transit_days, :estimated_cost, :rank)
      View = Data.define(:estimate_id, :origin, :destination, :arrival_deadline, :cargo_type,
                         :weight_kg, :status, :candidates) do
        def hazardous? = cargo_type == "HAZARDOUS"
      end

      def initialize(repository: Infrastructure::ActiveRecordEstimateRepository.new)
        @repository = repository
      end

      # 輸送見積作成（US01）。結果を :ok / :invalid / :no_route で返す。
      # 入力は positional ハッシュ or キーワードのいずれでも受ける。
      # routing/calculator は差し替え可能（テスト・合成ルート）。
      def create_estimate(params = nil, routing: Routing::Public::VoyageDirectory.new,
                          calculator: Billing::Public::FreightCalculator.new, **kwargs)
        input = params || kwargs
        Application::CreateEstimate.new(repository: @repository, routing: routing, calculator: calculator).call(
          origin: input[:origin], destination: input[:destination],
          arrival_deadline: input[:arrival_deadline], cargo_type: input[:cargo_type],
          weight_kg: input[:weight_kg]
        )
      end

      def find(estimate_id)
        estimate = @repository.find_by_estimate_id(estimate_id)
        estimate && to_view(estimate)
      end

      def all
        @repository.all.map { |e| to_view(e) }
      end

      private

      def to_view(estimate)
        View.new(
          estimate_id: estimate.estimate_id, origin: estimate.origin, destination: estimate.destination,
          arrival_deadline: estimate.arrival_deadline, cargo_type: estimate.cargo_type,
          weight_kg: estimate.weight_kg, status: estimate.status.value,
          candidates: estimate.candidates.map do |c|
            CandidateView.new(voyage_number: c.voyage_number, transit_port: c.transit_port,
                              transit_days: c.transit_days, estimated_cost: c.estimated_cost, rank: c.rank)
          end
        )
      end
    end
  end
end
