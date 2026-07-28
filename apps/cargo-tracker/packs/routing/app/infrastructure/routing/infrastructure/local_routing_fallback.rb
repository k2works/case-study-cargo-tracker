# frozen_string_literal: true

module Routing
  module Infrastructure
    # 外部経路システム障害時のフォールバック（ADR-0004）。
    # 自社の航海データ（過去実績）から、出発地→目的地に合致する経路候補を組み立てる。
    class LocalRoutingFallback
      def initialize(repository: ActiveRecordVoyageRepository.new)
        @repository = repository
      end

      # 直行の航海があれば単一区間の候補を返す（IT3 は直行のみを暫定フォールバックとする）。
      def candidates_for(request)
        @repository.all.filter_map do |voyage|
          next unless voyage.origin == request.origin && voyage.destination == request.destination

          transit = ((voyage.schedule.arrival_date - voyage.schedule.departure_date) / 86_400).ceil
          Domain::RouteCandidate.new(
            legs: [ { from: voyage.origin, to: voyage.destination, voyage_number: voyage.voyage_number.value } ],
            transit_days: transit, cost: nil, voyage_numbers: [ voyage.voyage_number.value ], fallback: true
          )
        end
      end
    end
  end
end
