# frozen_string_literal: true

module Billing
  module Infrastructure
    # 決済ゲートウェイの既定スタブ実装（US23・外部決済機関の Faraday/WebMock 導入までの暫定）。
    # MVP は常に入金確認成功として扱う。将来 PaymentGatewayPort の Faraday アダプタへ差し替える。
    class StubPaymentGateway < Domain::PaymentGatewayPort
      def confirm_payment(invoice_number:, amount:)
        Result.new(confirmed: true, transaction_reference: "STUB-#{invoice_number}")
      end
    end
  end
end
