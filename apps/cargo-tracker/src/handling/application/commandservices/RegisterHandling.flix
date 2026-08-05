///
/// 荷役作業の登録（US15。IT10）。
///
/// # 手順
///
/// 1. 追跡番号で貨物の写しを引く（ACL・読み）
/// 2. 集約が入力を検証し、写しと突き合わせて妥当性を判定する
/// 3. 記録を保存する
/// 4. 輸送状態を反映する（ACL・書き）
/// 5. MISROUTED なら予約へ報告する（ACL・書き）
///
/// **記録は必ず残す**。場所が予定と違っても記録を拒まない——
/// 予定と違うことは入力の誤りではなく**業務上起こりうる事実**であり、
/// 拒むと現場が記録できなくなる。ずれは妥当性として残し、警告で伝える。
///
/// **貨物が見つからなければ何も書かない**。追跡番号の打ち間違いは日常的に起きる。
///
mod HandlingRegisterHandling {
    use HandlingBookingMisroutingReport.{BookingMisroutingReport, ReportResult}
    use HandlingCargoSnapshotSource.CargoSnapshotSource
    use HandlingModel.{HandlingActivity, HandlingError, HandlingType, HandlingValidity}
    use HandlingRepo.HandlingRepo
    use HandlingTrackingTransportStatusUpdate.TrackingTransportStatusUpdate
    use SharedClock.Clock

    pub enum RegisterResult with Eq, ToString {
        /// 記録した（妥当性と反映結果を添えて返す。画面が注意文を出す）
        case Registered(HandlingValidity, ReflectionOutcome)
        /// 追跡番号に対応する貨物が無い
        case CargoNotFound
        /// 入力が成立しない
        case Rejected(HandlingError)
    }

    ///
    /// 他コンテキストへの反映の結果（IT11・M6）。
    ///
    /// IT10 は反映の結果を `discard` しており、失敗しても現場には
    /// 「登録しました」と出た。**荷主の画面が古いままであることを誰も知らない**
    /// 状態になり、気付くのは荷主が問い合わせてきたときになる。
    ///
    /// [ADR-0012](../../../../../../docs/adr/ADR-0012-cross-context-writes-go-through-the-target-aggregate.md)
    /// のリスク 2 は「反映が遅れる」と書いていたが、実際には**永久に反映されない**。
    /// 記述と実装が逆であり、ADR 側を訂正した。
    ///
    /// **両方落ちたら追跡を返す**。1 つしか出せないなら荷主が見る側を選ぶ——
    /// 経路逸脱の報告は経路設計者への電話で代替できるが、
    /// 荷主の追跡画面は誰も手で直せない。
    ///
    pub enum ReflectionOutcome with Eq, ToString {
        case Reflected
        /// 追跡へ反映できなかった（荷主の画面が古いまま）
        case TrackingNotReflected
        /// 経路逸脱を予約へ報告できなかった（経路設計者に届いていない）
        case MisroutingNotReported
    }

    pub def register(trackingNumber: String, handlingType: HandlingType,
                     location: String, completionTime: String,
                     voyageNumber: Option[String], operatorName: String):
            RegisterResult \ HandlingRepo + CargoSnapshotSource + Clock
                           + TrackingTransportStatusUpdate + BookingMisroutingReport =
        match HandlingModel.register(trackingNumber, handlingType, location,
                                     completionTime, voyageNumber, operatorName,
                                     Clock.today()) {
            case Err(e) => RegisterResult.Rejected(e)
            case Ok(activity) =>
                match CargoSnapshotSource.findByTrackingNumber(HandlingModel.trackingNumberOf(activity)) {
                    case None           => RegisterResult.CargoNotFound
                    case Some(snapshot) => {
                        let validity = HandlingModel.isValidFor(activity, snapshot);
                        let recorded = HandlingModel.withValidity(activity, validity);
                        HandlingRepo.save(recorded);
                        // **輸送状態は妥当性によらず進める**。貨物は実際に
                        // 積み込まれており、荷主が見る画面はその事実を出す必要がある。
                        // **記録そのものを渡す**（IT11・H8）——荷主のタイムラインに
                        // 「いつ・どこで」を出すために要る
                        let tracking = TrackingTransportStatusUpdate.apply(
                            recorded, HandlingModel.resultingStatusOf(handlingType));
                        let reported =
                            if (validity == HandlingValidity.Misrouted)
                                // **報告の失敗で荷役の記録を失わせない**。記録は済んでおり、
                                // ここで例外にすると現場は「登録できなかった」と受け取る。
                                // ただし**黙って捨てない**（IT11・M6）
                                BookingMisroutingReport.report(
                                    HandlingModel.bookingIdOf(snapshot))
                                    == ReportResult.Reported
                            else true;
                        RegisterResult.Registered(validity,
                                                  outcomeOf(tracking, reported))
                    }
                }
        }

    ///
    /// 反映の結果をまとめる。**追跡の失敗を優先する**（上記）。
    ///
    def outcomeOf(tracking: HandlingTrackingTransportStatusUpdate.UpdateResult,
                  reported: Bool): ReflectionOutcome =
        if (tracking != HandlingTrackingTransportStatusUpdate.UpdateResult.Updated)
            ReflectionOutcome.TrackingNotReflected
        else if (not reported)
            ReflectionOutcome.MisroutingNotReported
        else
            ReflectionOutcome.Reflected
}
