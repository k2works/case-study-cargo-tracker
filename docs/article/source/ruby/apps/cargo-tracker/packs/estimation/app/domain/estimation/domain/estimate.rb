# frozen_string_literal: true

require "securerandom"

module Estimation
  module Domain
    # 見積の集約ルート（US01 輸送見積作成）。輸送要件とルート候補を保持する。
    # origin/destination は共有カーネル Location の UN/LOCODE を論理参照する。
    class Estimate
      CARGO_TYPES = %w[GENERAL HAZARDOUS REFRIGERATED].freeze

      attr_reader :estimate_id, :origin, :destination, :arrival_deadline, :cargo_type,
                  :weight_kg, :candidates, :status

      # 見積を作成する（見積番号を採番し CREATED で開始）。
      def self.create(origin:, destination:, arrival_deadline:, cargo_type:, weight_kg:, estimate_id: nil)
        new(
          estimate_id: estimate_id || SecureRandom.uuid, origin: origin, destination: destination,
          arrival_deadline: arrival_deadline, cargo_type: cargo_type, weight_kg: weight_kg,
          candidates: [], status: EstimateStatus.initial
        )
      end

      # 永続化からの復元専用。
      def self.reconstitute(**attributes)
        new(**attributes)
      end

      def initialize(estimate_id:, origin:, destination:, arrival_deadline:, cargo_type:,
                     weight_kg:, candidates:, status:)
        raise ArgumentError, "出発地と目的地は異なる必要があります" if origin == destination
        raise ArgumentError, "貨物種別が不正です: #{cargo_type}" unless CARGO_TYPES.include?(cargo_type)

        weight = weight_kg.is_a?(BigDecimal) ? weight_kg : BigDecimal(weight_kg.to_s)
        raise ArgumentError, "重量は正である必要があります" if weight <= 0

        @estimate_id = estimate_id
        @origin = origin
        @destination = destination
        @arrival_deadline = arrival_deadline
        @cargo_type = cargo_type
        @weight_kg = weight
        @candidates = candidates
        @status = status
      end

      # ルート候補を一括で差し替える（US01 候補算出・US10 再算出）。
      def replace_candidates(new_candidates)
        @candidates = new_candidates
      end
    end
  end
end
