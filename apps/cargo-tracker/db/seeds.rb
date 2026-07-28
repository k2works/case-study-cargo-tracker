# frozen_string_literal: true

# 開発・動作確認用の初期利用者。冪等（find_or_create_by）に投入する。
# ログイン画面のデフォルト入力（development）と一致させる。
DEFAULT_PASSWORD = "password123"

seed_users = [
  { username: "sales",   email: "sales@example.com",   role: "sales" },
  { username: "handler", email: "handler@example.com", role: "handler" },
  { username: "tracker", email: "tracker@example.com", role: "tracker" },
  { username: "billing", email: "billing@example.com", role: "billing" },
  { username: "admin",   email: "admin@example.com",   role: "admin" }
]

seed_users.each do |attrs|
  user = User.find_or_create_by!(username: attrs[:username]) do |u|
    u.email = attrs[:email]
    u.password = DEFAULT_PASSWORD
    u.enabled = true
  end
  user.user_roles.find_or_create_by!(role: attrs[:role])
  puts "seed user: #{user.username} / #{DEFAULT_PASSWORD}（#{attrs[:role]}）"
end
