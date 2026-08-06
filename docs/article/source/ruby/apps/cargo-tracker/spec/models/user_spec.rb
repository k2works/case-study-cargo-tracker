# frozen_string_literal: true

require "rails_helper"

RSpec.describe User, type: :model do
  describe "バリデーション" do
    it "利用者 ID・メール・パスワードがあれば有効" do
      expect(build(:user)).to be_valid
    end

    it "利用者 ID がなければ無効" do
      expect(build(:user, username: nil)).not_to be_valid
    end

    it "利用者 ID は一意でなければならない" do
      create(:user, username: "alice")
      expect(build(:user, username: "alice")).not_to be_valid
    end

    it "メールアドレスは一意でなければならない" do
      create(:user, email: "alice@example.com")
      expect(build(:user, email: "alice@example.com")).not_to be_valid
    end
  end

  describe "#authenticate" do
    let(:user) { create(:user, password: "secret123") }

    it "正しいパスワードで認証できる" do
      expect(user.authenticate("secret123")).to eq(user)
    end

    it "誤ったパスワードでは認証できない" do
      expect(user.authenticate("wrong")).to be_falsey
    end
  end

  describe "ロール" do
    it "#role? で保持ロールを判定できる" do
      user = create(:user, :sales)
      expect(user.role?(:sales)).to be true
      expect(user.role?(:admin)).to be false
    end

    it "#roles で保持ロール一覧を取得できる" do
      user = create(:user, :sales)
      expect(user.roles).to contain_exactly("sales")
    end
  end

  describe "アカウントロック（US26）" do
    let(:user) { create(:user) }

    it "初期状態はロックされていない" do
      expect(user).not_to be_locked
    end

    it "認証失敗を記録すると失敗回数が増える" do
      expect { user.register_failure! }.to change { user.reload.failed_attempts }.from(0).to(1)
    end

    it "5 回連続失敗するとロックされる" do
      4.times { user.register_failure! }
      expect(user).not_to be_locked
      user.register_failure!
      expect(user.reload).to be_locked
    end

    it "認証成功で失敗回数がリセットされる" do
      3.times { user.register_failure! }
      user.reset_failures!
      expect(user.reload.failed_attempts).to eq(0)
    end
  end

  describe "有効・無効" do
    it "無効化された利用者は #active? が false" do
      expect(create(:user, :disabled)).not_to be_active
    end

    it "有効な利用者は #active? が true" do
      expect(create(:user)).to be_active
    end
  end
end
