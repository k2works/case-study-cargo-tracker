# frozen_string_literal: true

require "rails_helper"

# US21/US22/US23: 請求・精算の HTTP フロー（経理担当者ロール）。
RSpec.describe "請求・精算（US21/US22/US23）", type: :request do
  def sign_in_billing
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "billing")
    post login_path, params: { username: user.username, password: "secret123" }
  end

  let(:money) { ->(v) { Billing::Domain::MoneyAmount.new(amount: v, currency: "JPY") } }

  # PENDING の請求書を直接シードする（法人割引 10%）。
  def seed_invoice
    Billing::Infrastructure::ActiveRecordInvoiceRepository.new.save(
      Billing::Domain::Invoice.generate(
        invoice_number: "INV-000001", booking_id: "BKG-ABCD1234", shipper_id: 1,
        base_amount: money.call(100_000), discount_rate: Billing::Domain::DiscountRate.new(rate: BigDecimal("0.10")),
        tax_amount: money.call(9_000), total_amount: money.call(99_000), issued_at: Time.utc(2026, 11, 1)
      )
    )
    "INV-000001"
  end

  it "請求書一覧を表示できる" do
    sign_in_billing
    seed_invoice
    get billing_invoices_path
    expect(response).to have_http_status(:ok)
    expect(response.body).to include("請求書一覧")
    expect(response.body).to include("INV-000001")
  end

  it "請求書詳細で料金明細と割引根拠を表示する（US22）" do
    sign_in_billing
    number = seed_invoice
    get billing_invoice_path(number)
    expect(response.body).to include("基本料金")
    expect(response.body).to include("割引")     # 割引根拠
    expect(response.body).to include("消費税")
    expect(response.body).to include("99,000")   # 合計金額
  end

  it "入金確認で精算済（CONFIRMED）になる（US23）" do
    sign_in_billing
    number = seed_invoice
    post confirm_billing_invoice_path(number)
    follow_redirect!
    expect(response.body).to include("入金を確認しました")
    expect(Billing::Public::BillingService.new.find_invoice(number).payment_status).to eq("CONFIRMED")
  end

  it "DELIVERED でない予約の料金算出はエラーを返す（US21）" do
    sign_in_billing
    post billing_invoices_path, params: { booking_id: "BKG-NONEXIST" }
    follow_redirect!
    expect(response.body).to include("料金算出できません").or include("見つかりません")
  end

  it "billing 以外のロールはアクセスできない" do
    user = create(:user, password: "secret123")
    user.user_roles.create!(role: "sales")
    post login_path, params: { username: user.username, password: "secret123" }
    get billing_invoices_path
    expect(response).to have_http_status(:forbidden)
  end
end
