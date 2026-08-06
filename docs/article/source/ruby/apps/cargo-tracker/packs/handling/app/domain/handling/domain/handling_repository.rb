# frozen_string_literal: true

module Handling
  module Domain
    # 荷役の二重登録（冪等キーの一意制約違反・並行 POST の TOCTOU）を表すドメイン例外（T35）。
    class DuplicateHandlingActivity < StandardError; end

    # 荷役作業リポジトリの抽象ポート（出力ポート・ADR-0001）。
    class HandlingRepository
      def save(_activity) = raise NotImplementedError
      def history_for(_booking_id) = raise NotImplementedError
    end
  end
end
