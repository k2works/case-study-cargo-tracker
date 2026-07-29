# frozen_string_literal: true

module Estimation
  module Domain
    # 見積リポジトリの抽象ポート（出力ポート・ADR-0001）。
    class EstimateRepository
      def save(_estimate) = raise NotImplementedError
      def find_by_estimate_id(_estimate_id) = raise NotImplementedError
      def all = raise NotImplementedError
    end
  end
end
