# frozen_string_literal: true

require "rails_helper"

RSpec.describe AuthenticationService, type: :service do
  subject(:service) { described_class.new(logger: logger) }

  let(:logger) { instance_spy(ActiveSupport::Logger) }

  describe "#authenticate" do
    context "正しい認証情報" do
      let!(:user) { create(:user, username: "alice", password: "secret123") }

      it "認証に成功し利用者を返す" do
        result = service.authenticate("alice", "secret123")
        expect(result).to be_success
        expect(result.user).to eq(user)
      end

      it "成功がログに記録される" do
        service.authenticate("alice", "secret123")
        expect(logger).to have_received(:info).with(/ログイン成功.*alice/)
      end

      it "失敗回数がリセットされる" do
        user.update!(failed_attempts: 3)
        service.authenticate("alice", "secret123")
        expect(user.reload.failed_attempts).to eq(0)
      end
    end

    context "誤ったパスワード" do
      let!(:user) { create(:user, username: "alice", password: "secret123") }

      it "認証に失敗する" do
        result = service.authenticate("alice", "wrong")
        expect(result).not_to be_success
        expect(result.error_message).to eq("利用者 ID またはパスワードが正しくありません")
      end

      it "失敗回数が増え、失敗がログに記録される" do
        service.authenticate("alice", "wrong")
        expect(user.reload.failed_attempts).to eq(1)
        expect(logger).to have_received(:warn).with(/ログイン失敗.*alice/)
      end

      it "5 回連続失敗でアカウントがロックされ、ロック結果を返す" do
        4.times { service.authenticate("alice", "wrong") }
        result = service.authenticate("alice", "wrong")
        expect(user.reload).to be_locked
        expect(result.locked?).to be true
      end
    end

    context "存在しない利用者" do
      it "認証に失敗する（利用者列挙を避ける同一メッセージ）" do
        result = service.authenticate("ghost", "whatever")
        expect(result).not_to be_success
        expect(result.error_message).to eq("利用者 ID またはパスワードが正しくありません")
      end
    end

    context "ロック済みアカウント" do
      let!(:user) { create(:user, :locked, username: "alice", password: "secret123") }

      it "正しいパスワードでもログインできず、ロック案内を返す" do
        result = service.authenticate("alice", "secret123")
        expect(result).not_to be_success
        expect(result.locked?).to be true
      end
    end

    context "無効化されたアカウント" do
      let!(:user) { create(:user, :disabled, username: "alice", password: "secret123") }

      it "ログインできず、管理者への問い合わせを案内する" do
        result = service.authenticate("alice", "secret123")
        expect(result).not_to be_success
        expect(result.error_message).to match(/管理者/)
      end
    end
  end
end
