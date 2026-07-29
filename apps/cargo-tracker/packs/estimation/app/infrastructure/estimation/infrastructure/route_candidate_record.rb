# frozen_string_literal: true

module Estimation
  module Infrastructure
    # route_candidates テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class RouteCandidateRecord < ApplicationRecord
      self.table_name = "route_candidates"
    end
  end
end
