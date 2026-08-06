# frozen_string_literal: true

module Routing
  module Infrastructure
    # 外部経路システム障害時のフォールバック（ADR-0004）。
    # 自社の航海データ（過去実績）から、出発地→目的地に合致する経路候補を組み立てる。
    # 直行便に加え、寄港地での接続可能性を評価した 2 区間の乗り継ぎ候補も返す（US08）。
    class LocalRoutingFallback
      MAX_LEGS = 2 # IT5 は最大 2 区間（直行 + 1 回乗り継ぎ）まで評価する

      def initialize(repository: ActiveRecordVoyageRepository.new)
        @repository = repository
      end

      # 出発地→目的地に到達する経路候補を返す。直行を最優先、次に所要日数の短い順に並べる（US08）。
      def candidates_for(request)
        voyages = @repository.all
        candidates = direct_candidates(voyages, request) + transship_candidates(voyages, request)
        # 推奨順: 直行（区間数 1）を優先し、同条件では所要日数の短い順。
        candidates.sort_by { |c| [ c.legs.size, c.transit_days ] }
      end

      private

      def direct_candidates(voyages, request)
        voyages.filter_map do |voyage|
          next unless voyage.origin == request.origin && voyage.destination == request.destination

          build_candidate([ voyage ])
        end
      end

      # 寄港地接続評価（US08）: 出発地発の便 v1 と、v1 の到着港発・目的地着の便 v2 を、
      # v1 到着 ≤ v2 出発（接続可能）で連結した 2 区間候補を返す。
      def transship_candidates(voyages, request)
        firsts = voyages.select { |v| v.origin == request.origin && v.destination != request.destination }
        firsts.flat_map do |first|
          voyages.filter_map do |second|
            next unless second.origin == first.destination && second.destination == request.destination
            next unless connectable?(first, second)

            build_candidate([ first, second ])
          end
        end
      end

      # 接続可能性: 前便の到着が後便の出発以前であること。
      def connectable?(first, second)
        first.schedule.arrival_date <= second.schedule.departure_date
      end

      # 連続する航海列から経路候補を組み立てる。
      def build_candidate(voyages)
        legs = voyages.map do |v|
          { from: v.origin, to: v.destination, voyage_number: v.voyage_number.value,
            load_time: v.schedule.departure_date, unload_time: v.schedule.arrival_date }
        end
        departure = voyages.first.schedule.departure_date
        arrival = voyages.last.schedule.arrival_date
        Domain::RouteCandidate.new(
          legs: legs, transit_days: ((arrival - departure) / 86_400).ceil, cost: nil,
          voyage_numbers: voyages.map { |v| v.voyage_number.value }, fallback: true,
          arrival_date: arrival, carrier_names: voyages.map(&:carrier_name)
        )
      end
    end
  end
end
