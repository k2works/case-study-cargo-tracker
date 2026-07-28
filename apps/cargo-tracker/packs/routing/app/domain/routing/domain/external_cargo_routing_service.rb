# frozen_string_literal: true

module Routing
  module Domain
    # 外部経路探索の ACL 出力ポート（ADR-0004）。
    # 外部経路システムへ経路探索を依頼し RouteCandidate 一覧を返す。
    # 具象アダプタは Infrastructure 層で Faraday HTTP により実装し、
    # 接続タイムアウト時は過去実績データからフォールバック候補を返す。
    class ExternalCargoRoutingService
      # 経路探索リクエスト。
      SearchRequest = Data.define(:origin, :destination, :arrival_deadline)

      def search_routes(_request)
        raise NotImplementedError, "#{self.class}#search_routes is not implemented"
      end
    end
  end
end
