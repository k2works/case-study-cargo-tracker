# frozen_string_literal: true

module Handling
  module Domain
    # 荷受人確認（US16）。荷受人名と、署名または確認コードのいずれかを保持する。
    RecipientConfirmation = Data.define(:recipient_name, :signature, :confirmation_code) do
      def initialize(recipient_name:, signature: nil, confirmation_code: nil)
        super
      end

      # 荷受人名があり、署名または確認コードのいずれかが取得されていれば有効。
      def valid?
        recipient_name.to_s.strip.present? &&
          (signature.to_s.strip.present? || confirmation_code.to_s.strip.present?)
      end
    end
  end
end
