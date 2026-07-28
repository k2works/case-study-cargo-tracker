import { Controller } from "@hotwired/stimulus"

// 貨物種別の選択に応じて、危険物申告／温度条件のフィールドを動的に表示切替する（US05）。
export default class extends Controller {
  static targets = ["select", "hazardous", "refrigerated"]

  connect() {
    this.toggle()
  }

  toggle() {
    const type = this.selectTarget.value
    this.hazardousTarget.hidden = type !== "HAZARDOUS"
    this.refrigeratedTarget.hidden = type !== "REFRIGERATED"
  }
}
