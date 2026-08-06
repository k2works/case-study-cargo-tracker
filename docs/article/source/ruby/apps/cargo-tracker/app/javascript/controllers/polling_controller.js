import { Controller } from "@hotwired/stimulus"

// Turbo Frame を一定間隔で再読込し、追跡ステータスを差分更新する（US18・既定 30 秒）。
export default class PollingController extends Controller {
  static values = { interval: { type: Number, default: 30000 } }

  connect() {
    this.timer = setInterval(() => this.element.reload(), this.intervalValue)
  }

  disconnect() {
    clearInterval(this.timer)
  }
}
