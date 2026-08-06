# frozen_string_literal: true

module RuboCop
  module Cop
    module CargoTracker
      # ドメイン層（packs/*/app/domain/**）で ActiveRecord への依存を禁止する（ADR-0001）。
      # Packwerk では検出できないため RuboCop カスタム cop で担保し CI で失敗させる。
      #
      # @example
      #   # bad（packs/booking/app/domain 配下）
      #   class Cargo < ApplicationRecord; end
      #   ActiveRecord::Base.transaction { ... }
      #
      #   # good
      #   class Cargo; end # PORO
      class NoActiveRecordInDomain < Base
        MSG = "ドメイン層で ActiveRecord に依存しないでください（ADR-0001。PORO を維持し永続化は Infrastructure 層へ）。"

        FORBIDDEN = %i[ApplicationRecord ActiveRecord].freeze

        def on_const(node)
          return unless FORBIDDEN.include?(node.children.last)

          add_offense(node)
        end
      end
    end
  end
end
