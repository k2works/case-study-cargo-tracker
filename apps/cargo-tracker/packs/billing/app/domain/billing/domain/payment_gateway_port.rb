# frozen_string_literal: true

module Billing
  module Domain
    # 決済機関連携の出力ポート（ACL・US23）。入金確認を外部決済機関に委譲する。
    # Secondary Adapter（Faraday/WebMock）で実装を差し替える。
    class PaymentGatewayPort
      Result = Struct.new(:confirmed, :transaction_reference, keyword_init: true) do
        def confirmed? = confirmed
      end

      def confirm_payment(invoice_number:, amount:) = raise NotImplementedError
    end
  end
end
