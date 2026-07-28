import { Controller } from "@hotwired/stimulus"

// 荷役作業種別の選択に応じて、荷受人確認フィールド（引取 CLAIM 時のみ）を動的に表示切替する（US16）。
export default class extends Controller {
  static targets = ["eventType", "claimFields"]

  connect() {
    this.toggle()
  }

  toggle() {
    this.claimFieldsTarget.hidden = this.eventTypeTarget.value !== "CLAIM"
  }
}
