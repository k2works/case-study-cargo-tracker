# frozen_string_literal: true

module Handling
  module Domain
    # 荷役作業リポジトリの抽象ポート（出力ポート・ADR-0001）。
    class HandlingRepository
      def save(_activity) = raise NotImplementedError
      def history_for(_booking_id) = raise NotImplementedError
    end
  end
end
