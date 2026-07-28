# frozen_string_literal: true

require "rails_helper"

RSpec.describe Booking::Application::BookCargo do
  subject(:use_case) do
    described_class.new(repository: repository, shipper_existence_checker: checker)
  end

  let(:repository) { Booking::Infrastructure::ActiveRecordCargoRepository.new }
  let(:checker) { instance_double(Booking::Domain::ShipperExistenceChecker) }

  let(:shipper_id) do
    Shipper::Public::ShipperRegistration.new.call(
      shipper_type: "INDIVIDUAL", name: "山田太郎", email: "yamada@example.com", address: "東京"
    ).shipper_id
  end

  let(:params) do
    {
      shipper_id: shipper_id,
      cargo_type: "GENERAL",
      weight_kg: "1200.5",
      origin: "JPOSA",
      destination: "USLAX",
      arrival_deadline: "2026-12-01",
      description: "自動車部品"
    }
  end

  describe "#call（US04）" do
    context "存在する荷主" do
      before { allow(checker).to receive(:exists?).with(shipper_id).and_return(true) }

      it "予約が登録され予約番号が発行される（PRELIMINARY）" do
        result = use_case.call(**params)
        expect(result).to be_success
        expect(result.booking_id).to match(/\ABKG-/)
        expect(repository.find_by_booking_id(Booking::Domain::BookingId.new(value: result.booking_id))
          .booking_status.preliminary?).to be true
      end

      it "経路設計者に予約登録の通知が送信される（US04）" do
        notifier = instance_spy(Booking::Domain::RoutingNotifier)
        described_class.new(repository: repository, shipper_existence_checker: checker, notifier: notifier)
                       .call(**params)
        expect(notifier).to have_received(:notify_booking_registered).with(/\ABKG-/)
      end
    end

    context "存在しない荷主" do
      before { allow(checker).to receive(:exists?).with(999).and_return(false) }

      it "荷主が存在しないため登録に失敗する（ACL 経由の存在確認）" do
        result = use_case.call(**params.merge(shipper_id: 999))
        expect(result).not_to be_success
        expect(result.error_message).to match(/荷主/)
      end
    end

    context "危険物（US05）で申告なし" do
      before { allow(checker).to receive(:exists?).and_return(true) }

      it "日本語メッセージで失敗する" do
        result = use_case.call(**params.merge(cargo_type: "HAZARDOUS"))
        expect(result).not_to be_success
        expect(result.error_message).to match(/危険物申告/)
      end
    end
  end
end
