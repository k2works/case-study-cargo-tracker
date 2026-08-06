# frozen_string_literal: true

require "rails_helper"

RSpec.describe "認証（US26/US27）", type: :system do
  let!(:user) { create(:user, :sales, username: "alice", password: "secret123") }

  describe "ログイン（US26）" do
    it "正しい認証情報でログインし、ロール別ダッシュボードが表示される" do
      visit login_path
      fill_in "利用者 ID", with: "alice"
      fill_in "パスワード", with: "secret123"
      click_button "ログイン"

      expect(page).to have_current_path(root_path)
      expect(page).to have_content("ダッシュボード")
      expect(page).to have_content("営業担当者")
    end

    it "認証情報が一致しない場合はエラーメッセージを表示する" do
      visit login_path
      fill_in "利用者 ID", with: "alice"
      fill_in "パスワード", with: "wrong"
      click_button "ログイン"

      expect(page).to have_content("利用者 ID またはパスワードが正しくありません")
      expect(page).to have_current_path(login_path)
    end

    it "5 回連続で失敗するとアカウントがロックされる" do
      5.times do
        visit login_path
        fill_in "利用者 ID", with: "alice"
        fill_in "パスワード", with: "wrong"
        click_button "ログイン"
      end

      expect(user.reload).to be_locked

      # ロック後は正しいパスワードでもログインできない
      visit login_path
      fill_in "利用者 ID", with: "alice"
      fill_in "パスワード", with: "secret123"
      click_button "ログイン"
      expect(page).to have_content("管理者")
    end
  end

  describe "ログアウト（US27）" do
    it "ログアウトするとセッションが破棄されログイン画面に戻る" do
      visit login_path
      fill_in "利用者 ID", with: "alice"
      fill_in "パスワード", with: "secret123"
      click_button "ログイン"

      click_button "ログアウト"

      expect(page).to have_current_path(login_path)

      # ログアウト後に業務画面へ戻れない
      visit root_path
      expect(page).to have_current_path(login_path)
    end
  end
end
