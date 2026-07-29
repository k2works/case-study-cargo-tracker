import { Controller } from "@hotwired/stimulus"

// 例外種別の選択に応じて、紛失（LOST）時のエスカレーション注意を動的に表示切替する（US20）。
export default class ExceptionFormController extends Controller {
  static targets = ["type", "lostNotice"]

  connect() {
    this.toggle()
  }

  toggle() {
    this.lostNoticeTarget.hidden = this.typeTarget.value !== "LOST"
  }
}
