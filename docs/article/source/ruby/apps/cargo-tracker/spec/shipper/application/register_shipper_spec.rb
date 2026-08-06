# frozen_string_literal: true

require "rails_helper"

RSpec.describe Shipper::Application::RegisterShipper do
  subject(:use_case) { described_class.new(repository: repository) }

  let(:repository) { Shipper::Infrastructure::ActiveRecordShipperRepository.new }

  let(:individual_params) do
    {
      shipper_type: "INDIVIDUAL",
      name: "山田太郎",
      email: "yamada@example.com",
      phone: "03-1234-5678",
      address: "東京都千代田区"
    }
  end

  let(:corporate_params) do
    {
      shipper_type: "CORPORATE",
      name: "株式会社サンプル",
      email: "corp@example.com",
      address: "大阪府大阪市",
      contract_number: "C-0001",
      discount_rate: "0.15"
    }
  end

  describe "#call" do
    context "個人荷主（US02）" do
      it "登録に成功し荷主コードが発行される" do
        result = use_case.call(**individual_params)
        expect(result).to be_success
        expect(result.shipper.code.value).to match(/\ASHP-/)
        expect(repository.exists_by_email?("yamada@example.com")).to be true
      end
    end

    context "法人荷主（US03）" do
      it "契約情報とともに登録できる" do
        result = use_case.call(**corporate_params)
        expect(result).to be_success
        expect(result.shipper).to be_corporate
        expect(result.shipper.discount_rate.percentage).to eq(15)
      end

      it "割引率が 30% を超えると登録に失敗する" do
        result = use_case.call(**corporate_params.merge(discount_rate: "0.5"))
        expect(result).not_to be_success
        expect(result.error_message).to match(/割引率/)
      end

      it "割引率が未入力の場合は日本語メッセージで失敗する（内部例外を露出しない）" do
        result = use_case.call(**corporate_params.merge(discount_rate: nil))
        expect(result).not_to be_success
        expect(result.error_message).to eq("法人荷主には割引率が必須です")
      end
    end

    context "メールアドレス重複（US02）" do
      before { use_case.call(**individual_params) }

      it "既存荷主を伴う重複結果を返す" do
        result = use_case.call(**individual_params)
        expect(result).not_to be_success
        expect(result).to be_duplicate
        expect(result.existing_shipper.email).to eq("yamada@example.com")
      end
    end
  end
end
