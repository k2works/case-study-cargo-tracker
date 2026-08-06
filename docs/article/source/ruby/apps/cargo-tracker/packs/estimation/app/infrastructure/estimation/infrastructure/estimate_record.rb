# frozen_string_literal: true

module Estimation
  module Infrastructure
    # estimates テーブルの Active Record レコード（永続化アダプタ内部専用）。
    class EstimateRecord < ApplicationRecord
      self.table_name = "estimates"
    end
  end
end
