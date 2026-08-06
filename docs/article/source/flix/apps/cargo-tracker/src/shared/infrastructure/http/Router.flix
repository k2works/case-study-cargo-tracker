///
/// ルーティング表とパスパターンの照合。
///
/// ルートは「メソッド・パスパターン・必要ロール・トランザクションモード・ハンドラ」の
/// 5 つ組で表す。認可要件とトランザクションの要否をルート定義の一部にすることで、
/// 設定の付け忘れが起きない構造にしている。
///
/// **表は 1 つである**。IT3 では Flix の効果が不変であることを理由に効果集合ごとへ
/// 4 分割していたが、その形は「ルート表が認可の唯一の正典」という設計を壊していた
/// （同一パスが 2 表にあると先勝ちで片方の認可が静かに消える）。
/// 効果集合の相違は合成ルートの `lift` で吸収する（IT4・TS08）。
///
mod SharedHttpRouter {
    use SharedHttp.{HttpMethod, Request, RequiredRole, Response}
    use SharedHttp.PathPattern

    ///
    /// ルートが要求するトランザクションの種類。
    ///
    /// **ルート定義に持たせる理由**: トランザクションを開くかどうかは、
    /// ハンドラを呼ぶ**前**に決めなければならない。`/health/live` が DB を見ると、
    /// DB 障害時に再起動ループを起こす（IT3 レビュー H4）。ハンドラの中で判断する形にすると、
    /// 判断に至る前にコネクションを取ることになる。
    ///
    pub enum TxMode with Eq {
        /// DB を使わない。トランザクションを開かない
        case NoTx
        /// 読み取り専用トランザクション（コミットしない）
        case ReadOnly
        /// 書き込みトランザクション
        case Write
    }

    ///
    /// ルート定義。
    ///
    /// ハンドラは効果を要求しうるため、効果多相にはせず呼び出し側（合成ルート）で
    /// 効果集合を揃えた状態の関数を渡す。
    ///
    pub enum Route[ef: Eff](HttpMethod, PathPattern, RequiredRole, TxMode, Request -> Response \ ef)

    /// ルートのメソッド
    pub def routeMethod(r: Route[ef]): HttpMethod =
        let Route.Route(m, _, _, _, _) = r;
        m

    /// ルートのパスパターン
    pub def routePattern(r: Route[ef]): PathPattern =
        let Route.Route(_, p, _, _, _) = r;
        p

    /// ルートの必要ロール
    pub def routeRole(r: Route[ef]): RequiredRole =
        let Route.Route(_, _, role, _, _) = r;
        role

    /// ルートのトランザクションモード
    pub def routeTxMode(r: Route[ef]): TxMode =
        let Route.Route(_, _, _, mode, _) = r;
        mode

    /// ルートのハンドラ
    pub def routeHandler(r: Route[ef]): Request -> Response \ ef =
        let Route.Route(_, _, _, _, h) = r;
        h

    ///
    /// ルート解決の結果。
    ///
    /// **「見つからない」を 2 つに分ける**。パスは一致するがメソッドが違う場合は
    /// 405 であり、404 ではない。IT3 は両者を区別せず 404 を返していた。
    ///
    pub enum Resolution[ef: Eff] {
        case Found(Route[ef], Map[String, String])
        case MethodMismatch
        case NotFound
    }

    ///
    /// パスパターンと実パスを照合し、パスパラメータを取り出す。
    ///
    /// 例: `/public/tracking/{trackingNumber}` と `/public/tracking/TRK-001` を照合すると
    /// `Some({trackingNumber => TRK-001})` を返す。
    ///
    pub def matchPath(pattern: PathPattern, actualPath: String): Option[Map[String, String]] =
        let PathPattern.PathPattern(p) = pattern;
        let patternSegs = String.split(regex = "/", p);
        let actualSegs = String.split(regex = "/", actualPath);
        if (List.length(patternSegs) != List.length(actualSegs))
            None
        else
            matchSegments(patternSegs, actualSegs, Map.empty())

    /// セグメント単位の照合
    def matchSegments(patternSegs: List[String], actualSegs: List[String],
                      acc: Map[String, String]): Option[Map[String, String]] =
        match (patternSegs, actualSegs) {
            case (Nil, Nil) => Some(acc)
            case (ps :: pRest, actual :: aRest) =>
                if (String.startsWith(prefix = "{", ps) and String.endsWith(suffix = "}", ps))
                    let name = String.slice(start = 1, end = String.length(ps) - 1, ps);
                    matchSegments(pRest, aRest, Map.insert(name, actual, acc))
                else if (ps == actual)
                    matchSegments(pRest, aRest, acc)
                else
                    None
            case _ => None
        }

    ///
    /// ルーティング表から、メソッドとパスに一致するルートを探す。
    ///
    /// **パスの一致とメソッドの一致を分けて評価する**。パスだけ一致するルートが
    /// 1 つでもあれば `MethodMismatch`（405）とし、1 つもなければ `NotFound`（404）とする。
    ///
    pub def resolve(routes: List[Route[ef]], m: HttpMethod, actualPath: String): Resolution[ef] =
        match findMatching(routes, m, actualPath) {
            case Some((route, params)) => Resolution.Found(route, params)
            case None                  =>
                if (List.exists(r -> not Option.isEmpty(matchPath(routePattern(r), actualPath)), routes))
                    Resolution.MethodMismatch
                else
                    Resolution.NotFound
        }

    /// メソッドとパスの両方が一致する最初のルート
    def findMatching(routes: List[Route[ef]], m: HttpMethod,
                     actualPath: String): Option[(Route[ef], Map[String, String])] =
        match routes {
            case Nil => None
            case r :: rest =>
                if (routeMethod(r) == m)
                    match matchPath(routePattern(r), actualPath) {
                        case Some(params) => Some((r, params))
                        case None         => findMatching(rest, m, actualPath)
                    }
                else
                    findMatching(rest, m, actualPath)
        }

    ///
    /// 二重定義されているルートのキー（`"GET /bookings"` の形）を列挙する。
    ///
    /// 表が 1 つになっても、同じ表の中に同じメソッド・同じパターンを 2 度書けば
    /// **先に書いた方が勝ち、後の認可要件は静かに消える**。人手のレビューに頼らず、
    /// 起動時とテストで検出する（IT3 レビュー H16）。
    ///
    pub def duplicateKeys(routes: List[Route[ef]]): List[String] =
        List.map(routeKey, routes)
            |> List.foldLeft((acc, key) -> Map.insertWith((_, n) -> n + 1, key, 1, acc), Map.empty())
            |> Map.toList
            |> List.filter(pair -> snd(pair) > 1)
            |> List.map(fst)
            |> List.sort

    /// ルートの一意キー。メソッドとパターンの組で表す
    pub def routeKey(r: Route[ef]): String =
        "${methodName(routeMethod(r))} ${patternText(routePattern(r))}"

    /// パスパターンの文字列
    pub def patternText(p: PathPattern): String =
        let PathPattern.PathPattern(s) = p;
        s

    /// メソッドの表示名
    pub def methodName(m: HttpMethod): String = match m {
        case HttpMethod.Get    => "GET"
        case HttpMethod.Post   => "POST"
        case HttpMethod.Put    => "PUT"
        case HttpMethod.Delete => "DELETE"
    }
}
