/**
 * 追跡のモック（US17・US18・US19・US20）。
 *
 * <p><strong>本物と同じ規則で拒む。</strong>モックだけが甘いと、画面は「動く」まま本番で
 * 落ちる。規則を写すときは、本物の該当箇所を開いて条件を読み比べる（IT5 の Try 4）。
 *
 * <p>読み比べる本物（Phase 4 で実装する）:
 * <ul>
 *   <li>{@code TrackingStatus#canAdvanceTo} — <strong>戻る向きには進めない</strong>。
 *       進行の道の外にある EXCEPTION / UNKNOWN は、どちらの向きにも動かない
 *   <li>{@code TrackingActivity#raiseException} / {@code #resolveException} —
 *       発生前の状態に戻る。多重起票は断る（[ADR-024] 決定 2）
 *   <li>{@code ExceptionType} — 紛失だけが緊急。起票できるのは 3 種別（決定 3・11）
 *   <li>公開照会が返す項目（決定 5）——<strong>返さないものをここでも返さない</strong>
 * </ul>
 */
import { HttpResponse, http } from "msw";
import { formatBusinessDateTime } from "../../lib/business-time";
import { API_PATHS } from "../../config/api";
import { handlingActivities } from "./handling";

/** 進行の道。**並び順を持たない値（EXCEPTION / UNKNOWN）はここに無い**。 */
const PROGRESS = [
  "NOT_RECEIVED",
  "RECEIVED",
  "LOADED",
  "ONBOARD_CARRIER",
  "UNLOADED",
  "AWAITING_CLAIM",
  "CLAIMED",
] as const;

const STATUS_LABELS: Record<string, string> = {
  NOT_RECEIVED: "受領待ち",
  RECEIVED: "受領済み",
  LOADED: "積込済み",
  ONBOARD_CARRIER: "輸送中",
  UNLOADED: "荷降し済み",
  AWAITING_CLAIM: "引取待ち",
  CLAIMED: "引取済み",
  EXCEPTION: "例外発生",
  UNKNOWN: "不明",
};

/**
 * 起票できる例外の種別（[ADR-024] 決定 11）。
 *
 * MISROUTE は US28 が自動で、CUSTOMS_HOLD は US29 が起票する。**選択肢に出さない**
 * ——一覧に行だけ出て押せない形を作らない。
 */
const RAISABLE_EXCEPTION_TYPES = [
  { exceptionType: "DELAY", label: "遅延", urgent: false },
  { exceptionType: "DAMAGE", label: "破損", urgent: false },
  { exceptionType: "LOST", label: "紛失", urgent: true },
] as const;

/** 荷役の種別から進む先（本物の `TrackingStatus#afterHandling` の写し）。 */
const AFTER_HANDLING: Record<string, string> = {
  RECEIVE: "RECEIVED",
  LOAD: "LOADED",
  UNLOAD: "UNLOADED",
  CLAIM: "CLAIMED",
};

type MockException = {
  id: number;
  trackingNumber: string;
  exceptionType: string;
  description: string;
  occurredAt: string;
  urgent: boolean;
  resolvedAt: string | null;
  resolutionNotes: string | null;
};

type MockTracking = {
  trackingNumber: string;
  bookingId: string;
  status: string;
  /** **発生前の状態は行に持つ**（[ADR-024] 決定 2）。履歴から導かない */
  statusBefore: string | null;
  locationUnLocode: string;
  locationName: string;
  /** **決まっていなければ null**。0 や今日で埋めない（US18-2） */
  estimatedArrival: string | null;
  manualEvents: { occurredAt: string; status: string; locationName: string }[];
  notices: { noticedAt: string; message: string }[];
};

/** すでに追跡へ反映した荷役の記録。**同じ記録を 2 回進めない**。 */
const appliedHandlingIds = new Set<number>();

export const trackings: MockTracking[] = [];
export const trackingExceptions: MockException[] = [];
let exceptionIdSequence = 0;

/** 種データを作り直す。テストの順序で混ざらないよう、毎回まっさらにする。 */
export function resetTrackings() {
  trackings.length = 0;
  trackingExceptions.length = 0;
  exceptionIdSequence = 0;
  appliedHandlingIds.clear();
  trackings.push({
    trackingNumber: "TRK-20260823-0001",
    bookingId: "BKG-2026000004",
    status: "NOT_RECEIVED",
    statusBefore: null,
    locationUnLocode: "JPTYO",
    locationName: "Tokyo",
    estimatedArrival: "2027-09-15",
    manualEvents: [],
    notices: [],
  });
}

resetTrackings();

function find(trackingNumber: string) {
  return trackings.find(
    (tracking) => tracking.trackingNumber === trackingNumber,
  );
}

function activeExceptionOf(trackingNumber: string) {
  return trackingExceptions.find(
    (exception) =>
      exception.trackingNumber === trackingNumber &&
      exception.resolvedAt === null,
  );
}

/** 進む向きにしか動かない（本物の `canAdvanceTo` の写し）。道の外の値はどちらの向きにも偽。 */
function canAdvanceTo(current: string, next: string) {
  const from = PROGRESS.indexOf(current as (typeof PROGRESS)[number]);
  const to = PROGRESS.indexOf(next as (typeof PROGRESS)[number]);
  return from >= 0 && to >= 0 && to > from;
}

/** 荷役の記録と手動更新を、起きた順に 1 本にまとめる（US18-3）。 */
function eventsOf(tracking: MockTracking) {
  const fromHandling = handlingActivities
    .filter((activity) => activity.bookingId === tracking.bookingId)
    .flatMap((activity) => {
      const status = AFTER_HANDLING[activity.type];
      return status === undefined
        ? []
        : [
            {
              occurredAt: activity.completionTime,
              status,
              statusLabel: STATUS_LABELS[status],
              locationName: activity.locationName,
            },
          ];
    });
  const manual = tracking.manualEvents.map((event) => ({
    ...event,
    statusLabel: STATUS_LABELS[event.status],
  }));
  // **本物と同じ形で返す。**サーバは業務の暦で整形した文字列を返す（ADR-010）
  // ——ここが ISO のままだと、画面が本番で別の見え方になる
  return [...fromHandling, ...manual]
    .sort((a, b) => a.occurredAt.localeCompare(b.occurredAt))
    .map((event) => ({
      ...event,
      occurredAt: formatBusinessDateTime(event.occurredAt),
    }));
}

/**
 * 届いた荷役の記録を、追跡の状態へ反映する。
 *
 * <p><strong>状態は行が持つ。履歴から導かない。</strong>読むたびに履歴から計算し直すと、
 * 例外の発生前状態（`statusBefore`）が保存されていなくても正しく見えてしまう
 * ——[ADR-024] 決定 2 が避けようとしている欠陥そのものを、モックが持つことになる。
 *
 * <p>本物は購読側（`AdvanceTrackingUseCase`）が同じことを 1 回だけ行う。
 */
function applyHandlingEvents(tracking: MockTracking) {
  if (tracking.status === "EXCEPTION") {
    // 例外のあいだは荷役でも動かさない。動かすと、解決したときの戻り先が変わる
    return;
  }
  for (const activity of handlingActivities) {
    if (
      activity.bookingId !== tracking.bookingId ||
      appliedHandlingIds.has(activity.id)
    ) {
      continue;
    }
    const next = AFTER_HANDLING[activity.type];
    appliedHandlingIds.add(activity.id);
    if (next !== undefined && canAdvanceTo(tracking.status, next)) {
      tracking.status = next;
      tracking.locationName = activity.locationName;
    }
  }
}

function currentStatusOf(tracking: MockTracking) {
  applyHandlingEvents(tracking);
  return tracking.status;
}

function currentLocationOf(tracking: MockTracking) {
  applyHandlingEvents(tracking);
  return tracking.locationName;
}

function managedView(tracking: MockTracking) {
  const active = activeExceptionOf(tracking.trackingNumber);
  const status = currentStatusOf(tracking);
  return {
    trackingNumber: tracking.trackingNumber,
    bookingId: tracking.bookingId,
    status,
    statusLabel: STATUS_LABELS[status],
    locationName: currentLocationOf(tracking),
    estimatedArrival: tracking.estimatedArrival,
    activeException:
      active === undefined
        ? null
        : {
            id: active.id,
            exceptionType: active.exceptionType,
            label:
              RAISABLE_EXCEPTION_TYPES.find(
                (choice) => choice.exceptionType === active.exceptionType,
              )?.label ?? active.exceptionType,
            description: active.description,
            occurredAt: active.occurredAt,
            urgent: active.urgent,
          },
    events: eventsOf(tracking),
    // US19-5。**解決したものも含む**
    exceptionHistory: trackingExceptions
      .filter((exception) => exception.trackingNumber === tracking.trackingNumber)
      .map((exception) => ({
        exceptionType: exception.exceptionType,
        label:
          RAISABLE_EXCEPTION_TYPES.find(
            (choice) => choice.exceptionType === exception.exceptionType,
          )?.label ?? exception.exceptionType,
        description: exception.description,
        occurredAt: formatBusinessDateTime(exception.occurredAt),
        resolvedAt:
          exception.resolvedAt === null
            ? null
            : formatBusinessDateTime(exception.resolvedAt),
        resolutionNotes: exception.resolutionNotes ?? null,
        urgent: exception.urgent,
      })),
  };
}

/**
 * 公開照会が返すもの（[ADR-024] 決定 5）。
 *
 * **返さないものをここでも返さない。** 予約番号・作業者・航海番号・例外の詳細は載せない。
 * モックが余分に返すと、画面がそれを使ってしまい、本番で消える。
 */
function publicView(tracking: MockTracking) {
  const active = activeExceptionOf(tracking.trackingNumber);
  const status = currentStatusOf(tracking);
  return {
    trackingNumber: tracking.trackingNumber,
    status,
    statusLabel: STATUS_LABELS[status],
    locationName: currentLocationOf(tracking),
    estimatedArrival: tracking.estimatedArrival,
    hasException: active !== undefined,
    urgent: active?.urgent ?? false,
    events: eventsOf(tracking),
    notices: tracking.notices.map((notice) => ({
      ...notice,
      noticedAt: formatBusinessDateTime(notice.noticedAt),
    })),
  };
}

/** 通知は送らず、送った事実だけを残す（[ADR-024] 決定 9）。 */
function notice(tracking: MockTracking, message: string) {
  tracking.notices.push({ noticedAt: new Date().toISOString(), message });
}

/**
 * 見つからないときの文言。**本物と同じ文にする**。
 *
 * モックが本物より甘い（違う本文を返す）と、画面は開発中だけ正しく見える。
 * 実際、本物は Spring の既定で本文から文言が落ちており、モックだけが案内を
 * 返していた（IT9 返済枠 0.3）。文言は `PublicTrackingController.NOT_FOUND_MESSAGE`。
 */
const NOT_FOUND_MESSAGE =
  "追跡番号が見つかりません。追跡番号は TRK- で始まります（予約番号 BKG- では引けません）。番号をお確かめのうえ、もう一度入力してください";

/**
 * 公開照会の上限（本物の `PublicLookupThrottleFilter` の写し）。
 *
 * **本物にあってモックに無い応答は、画面が一度も通らない経路になる。** 429 が無いと、
 * 上限に当たった荷主に何が見えるかを誰も確かめないまま出すことになる（IT9 返済枠 0.3）。
 *
 * 窓と上限は本物と同じ（1 分・30 回。`app.public-lookup.limit-per-minute`）。
 * 本物は IP ごとに数えるが、ブラウザからは自分の IP しか無いので 1 つの窓で数える。
 */
const LOOKUP_WINDOW_MS = 60_000;
const LOOKUP_LIMIT_PER_WINDOW = 30;
let lookupWindowStartedAt = 0;
let lookupCount = 0;

/** 上限を超えたか。超えたときだけ true。 */
function exceedsLookupLimit(now: number): boolean {
  if (now - lookupWindowStartedAt >= LOOKUP_WINDOW_MS) {
    lookupWindowStartedAt = now;
    lookupCount = 0;
  }
  lookupCount += 1;
  return lookupCount > LOOKUP_LIMIT_PER_WINDOW;
}

/** テストが窓を開け直せるようにする。本物では時計が進むのを待つ。 */
export function resetLookupThrottle() {
  lookupWindowStartedAt = 0;
  lookupCount = 0;
}

/** 上限に当たった状態を作る。本物では同じ IP から短時間に叩くと起きる。 */
export function forceLookupThrottle() {
  lookupWindowStartedAt = Date.now();
  lookupCount = LOOKUP_LIMIT_PER_WINDOW;
}

export const trackingHandlers = [
  http.get(API_PATHS.publicTracking(":trackingNumber"), ({ params }) => {
    // 上限は見つかるかどうかより先に効く。見つからない照会こそ総当たりの本体である
    if (exceedsLookupLimit(Date.now())) {
      return HttpResponse.json(
        { message: "照会が多すぎます。しばらくしてからお試しください" },
        { status: 429 },
      );
    }
    const tracking = find(String(params.trackingNumber));
    if (tracking === undefined) {
      return HttpResponse.json({ message: NOT_FOUND_MESSAGE }, { status: 404 });
    }
    return HttpResponse.json(publicView(tracking));
  }),

  http.get(`${API_PATHS.trackingManagement}/exception-types`, () =>
    HttpResponse.json(RAISABLE_EXCEPTION_TYPES),
  ),

  http.get(`${API_PATHS.trackingManagement}/exceptions/open`, () => {
    const open = trackingExceptions.filter(
      (exception) => exception.resolvedAt === null,
    );
    return HttpResponse.json({
      count: open.length,
      urgentCount: open.filter((exception) => exception.urgent).length,
    });
  }),

  http.get(`${API_PATHS.trackingManagement}/exceptions`, () =>
    HttpResponse.json(
      trackings
        .filter(
          (tracking) =>
            activeExceptionOf(tracking.trackingNumber) !== undefined,
        )
        .map(managedView),
    ),
  ),

  http.get(
    `${API_PATHS.trackingManagement}/:trackingNumber/statuses`,
    ({ params }) => {
      const tracking = find(String(params.trackingNumber));
      if (tracking === undefined) {
        return HttpResponse.json(
          { message: "追跡番号が見つかりません" },
          { status: 404 },
        );
      }
      const current = currentStatusOf(tracking);
      return HttpResponse.json(
        PROGRESS.filter((status) => canAdvanceTo(current, status)).map(
          (status) => ({
            status,
            label: STATUS_LABELS[status],
          }),
        ),
      );
    },
  ),

  http.get(`${API_PATHS.trackingManagement}/:trackingNumber`, ({ params }) => {
    const tracking = find(String(params.trackingNumber));
    if (tracking === undefined) {
      return HttpResponse.json(
        { message: "追跡番号が見つかりません" },
        { status: 404 },
      );
    }
    return HttpResponse.json(managedView(tracking));
  }),

  http.post(
    `${API_PATHS.trackingManagement}/exceptions/:id/resolve`,
    async ({ request }) => {
      const body = (await request.json()) as {
        trackingNumber: string;
        exceptionId: number;
        resolutionNotes: string;
        newEstimatedArrival: string | null;
      };
      const tracking = find(body.trackingNumber);
      const exception = trackingExceptions.find(
        (candidate) => candidate.id === body.exceptionId,
      );
      if (tracking === undefined || exception === undefined) {
        return HttpResponse.json(
          { message: "追跡番号が見つかりません" },
          { status: 404 },
        );
      }
      if (body.resolutionNotes.trim() === "") {
        return HttpResponse.json(
          { message: "対応内容を入力してください" },
          { status: 400 },
        );
      }
      exception.resolvedAt = new Date().toISOString();
    exception.resolutionNotes = body.resolutionNotes;
      // **発生前の状態に戻す。**履歴から導かない（[ADR-024] 決定 2）
      tracking.status = tracking.statusBefore ?? tracking.status;
      tracking.statusBefore = null;
      if (
        body.newEstimatedArrival !== null &&
        body.newEstimatedArrival !== ""
      ) {
        tracking.estimatedArrival = body.newEstimatedArrival;
      }
      notice(tracking, `例外に対応しました: ${body.resolutionNotes}`);
      return HttpResponse.json(managedView(tracking));
    },
  ),

  http.post(
    `${API_PATHS.trackingManagement}/exceptions`,
    async ({ request }) => {
      const body = (await request.json()) as {
        trackingNumber: string;
        exceptionType: string;
        description: string;
      };
      const tracking = find(body.trackingNumber);
      if (tracking === undefined) {
        return HttpResponse.json(
          { message: "追跡番号が見つかりません" },
          { status: 404 },
        );
      }
      const choice = RAISABLE_EXCEPTION_TYPES.find(
        (candidate) => candidate.exceptionType === body.exceptionType,
      );
      if (choice === undefined) {
        // MISROUTE / CUSTOMS_HOLD は自動で起票される（[ADR-024] 決定 11）
        return HttpResponse.json(
          { message: "その例外は手では起票できません" },
          { status: 400 },
        );
      }
      if (body.description.trim() === "") {
        return HttpResponse.json(
          { message: "発生状況を入力してください" },
          { status: 400 },
        );
      }
      if (activeExceptionOf(body.trackingNumber) !== undefined) {
        // **多重起票を許さない。**発生前の状態が EXCEPTION で上書きされ、解決しても戻れない
        return HttpResponse.json(
          {
            message: "この貨物には未解決の例外があります。先に解決してください",
          },
          { status: 409 },
        );
      }
      exceptionIdSequence += 1;
      trackingExceptions.push({
        id: exceptionIdSequence,
        trackingNumber: body.trackingNumber,
        exceptionType: body.exceptionType,
        description: body.description,
        occurredAt: new Date().toISOString(),
        urgent: choice.urgent,
        resolvedAt: null,
        resolutionNotes: null,
      });
      tracking.statusBefore = currentStatusOf(tracking);
      tracking.status = "EXCEPTION";
      notice(tracking, `${choice.label}が発生しました`);
      return HttpResponse.json(managedView(tracking));
    },
  ),

  http.post(API_PATHS.trackingManagement, async ({ request }) => {
    const body = (await request.json()) as {
      trackingNumber: string;
      status: string;
      locationUnLocode: string;
      occurredAt: string;
    };
    const tracking = find(body.trackingNumber);
    if (tracking === undefined) {
      return HttpResponse.json(
        { message: "追跡番号が見つかりません" },
        { status: 404 },
      );
    }
    if (!canAdvanceTo(currentStatusOf(tracking), body.status)) {
      // **直す手段を伝える。**「できません」で終わらせない（[ADR-024] 決定 1）
      return HttpResponse.json(
        {
          message:
            "前の状態には戻せません。誤りを直すには、例外として起票してください",
        },
        { status: 409 },
      );
    }
    tracking.manualEvents.push({
      occurredAt: body.occurredAt,
      status: body.status,
      locationName:
        body.locationUnLocode === "JPTYO" ? "Tokyo" : body.locationUnLocode,
    });
    tracking.status = body.status;
    notice(tracking, `${STATUS_LABELS[body.status]}になりました`);
    return HttpResponse.json(managedView(tracking));
  }),
];
