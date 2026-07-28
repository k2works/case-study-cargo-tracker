# frozen_string_literal: true

# JS 依存の受け入れ基準（動的表示・Turbo 等）を検証するための Playwright ドライバ（T11）。
# `:js` メタデータを付けた system spec はヘッドレス Chromium で駆動する。
require "capybara-playwright-driver"

Capybara.register_driver(:playwright_headless) do |app|
  Capybara::Playwright::Driver.new(app, browser_type: :chromium, headless: true)
end

# Playwright のブラウザが導入済みか（未導入環境では :js を skip し全体を緑に保つ）。
PLAYWRIGHT_AVAILABLE = begin
  cache = ENV.fetch("PLAYWRIGHT_BROWSERS_PATH", File.expand_path("~/Library/Caches/ms-playwright"))
  Dir.exist?(cache) && !Dir.glob(File.join(cache, "chromium_headless_shell-*")).empty?
rescue StandardError
  false
end

RSpec.configure do |config|
  config.before(:each, :js, type: :system) do
    skip "Playwright ブラウザ未導入のため :js をスキップ（npx playwright install chromium-headless-shell）" unless PLAYWRIGHT_AVAILABLE
  end

  config.before(:each, :js, type: :system) do
    # Playwright は実 HTTP でアプリサーバに接続するため localhost 接続を許可する。
    WebMock.disable_net_connect!(allow_localhost: true)
    driven_by :playwright_headless
  end

  config.after(:each, :js, type: :system) do
    WebMock.disable_net_connect!(allow_localhost: false)
  end
end
