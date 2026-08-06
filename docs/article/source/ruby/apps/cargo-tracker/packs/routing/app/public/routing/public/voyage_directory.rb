# frozen_string_literal: true

module Routing
  module Public
    # 航海の公開ファサード（アプリ層＝合成ルート／他 BC 向け）。
    # 内部のユースケース・リポジトリを隠蔽し、公開ビューを返す（ADR-0001/0003/0004）。
    class VoyageDirectory
      # 航海の公開ビュー（内部集約を晒さない投影）。
      View = Data.define(:voyage_number, :carrier_name, :ship_name, :origin, :destination,
                         :departure_date, :arrival_date, :ports, :supported_cargo_types)

      def initialize(repository: Infrastructure::ActiveRecordVoyageRepository.new)
        @repository = repository
      end

      # 航海スケジュール登録（US24）。結果は RegisterVoyage::Result。
      def register(**params)
        Application::RegisterVoyage.new(repository: @repository).call(**params)
      end

      # 航海スケジュール更新（US25）。結果は UpdateSchedule::Result。
      def update(**params)
        Application::UpdateSchedule.new(repository: @repository).call(**params)
      end

      # 航海検索（US07）。結果は検索ビューの配列。
      def search(**params)
        Application::SearchVoyages.new(repository: @repository).call(**params)
      end

      # 経路候補の公開ビュー（内部 RouteCandidate VO を境界外へ晒さない射影・T18）。
      RouteCandidateView = Data.define(:route_path, :voyage_numbers, :carrier_names,
                                       :transit_days, :cost, :arrival_date, :direct, :fallback, :legs) do
        def direct? = direct
        def fallback? = fallback
      end

      # 経路候補算出（US08・一時計算値・ADR-0004）。結果は Result（成功時 candidates は公開ビュー配列）。
      # 外部経路 ACL の URL は環境変数 EXTERNAL_ROUTING_URL（未設定時は即フォールバック）。
      CandidatesResult = Struct.new(:candidates, :message, keyword_init: true) do
        def success? = candidates.present?
      end

      def calculate_route_candidates(origin:, destination:, arrival_deadline:, departure_date: nil)
        service = Infrastructure::ExternalCargoRoutingClient.new(
          base_url: ENV.fetch("EXTERNAL_ROUTING_URL", "http://localhost:1"),
          fallback: Infrastructure::LocalRoutingFallback.new(repository: @repository)
        )
        result = Application::CalculateRouteCandidates.new(routing_service: service).call(
          origin: origin, destination: destination, arrival_deadline: arrival_deadline, departure_date: departure_date
        )
        return CandidatesResult.new(message: result.message) unless result.success?

        CandidatesResult.new(candidates: result.candidates.map { |c| to_candidate_view(c) })
      end

      private

      def to_candidate_view(candidate)
        RouteCandidateView.new(
          route_path: [ candidate.legs.first[:from] ] + candidate.legs.map { |l| l[:to] },
          voyage_numbers: candidate.voyage_numbers, carrier_names: candidate.carrier_names,
          transit_days: candidate.transit_days, cost: candidate.cost,
          arrival_date: candidate.arrival_date, direct: candidate.direct?, fallback: candidate.fallback?,
          legs: candidate.legs.map { |l| to_leg_data(l) }
        )
      end

      # 経路候補の脚をプリミティブ Hash に射影する（BC 境界の ACL・旅程紐付け US09/US11 で消費）。
      def to_leg_data(leg)
        {
          load_location: leg[:from], unload_location: leg[:to], voyage_number: leg[:voyage_number],
          load_time: leg[:load_time], unload_time: leg[:unload_time]
        }
      end

      public

      def all
        @repository.all.map { |v| to_view(v) }
      end

      def find(voyage_number)
        voyage = @repository.find_by_number(Domain::VoyageNumber.new(value: voyage_number))
        voyage && to_view(voyage)
      rescue ArgumentError
        nil
      end

      private

      def to_view(voyage)
        View.new(
          voyage_number: voyage.voyage_number.value, carrier_name: voyage.carrier_name,
          ship_name: voyage.ship_name, origin: voyage.origin, destination: voyage.destination,
          departure_date: voyage.schedule.departure_date, arrival_date: voyage.schedule.arrival_date,
          ports: voyage.ports, supported_cargo_types: voyage.supported_cargo_types
        )
      end
    end
  end
end
