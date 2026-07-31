///
/// ルーティング表とパスパターンの照合。
///
/// ルートは「メソッド・パスパターン・必要ロール・ハンドラ」の 4 つ組で表す。
/// 認可要件をルート定義の一部にすることで、設定の付け忘れが起きない構造にしている。
///
mod SharedHttpRouter {
    use SharedHttp.{HttpMethod, Request, RequiredRole, Response}
    use SharedHttp.PathPattern

    ///
    /// ルート定義。
    ///
    /// ハンドラは効果を要求しうるため、効果多相にはせず呼び出し側（Server）で
    /// ハンドラを適用した状態の関数を渡す。
    ///
    pub enum Route[ef: Eff](HttpMethod, PathPattern, RequiredRole, Request -> Response \ ef)

    /// ルートのメソッド
    pub def routeMethod(r: Route[ef]): HttpMethod =
        let Route.Route(m, _, _, _) = r;
        m

    /// ルートのパスパターン
    pub def routePattern(r: Route[ef]): PathPattern =
        let Route.Route(_, p, _, _) = r;
        p

    /// ルートの必要ロール
    pub def routeRole(r: Route[ef]): RequiredRole =
        let Route.Route(_, _, role, _) = r;
        role

    /// ルートのハンドラ
    pub def routeHandler(r: Route[ef]): Request -> Response \ ef =
        let Route.Route(_, _, _, h) = r;
        h

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
    /// ルーティング表から、メソッドとパスに一致する最初のルートを探す。
    /// 見つかった場合はルートとパスパラメータを返す。
    ///
    pub def resolve(routes: List[Route[ef]], m: HttpMethod, actualPath: String):
            Option[(Route[ef], Map[String, String])] =
        match routes {
            case Nil => None
            case r :: rest =>
                if (routeMethod(r) == m)
                    match matchPath(routePattern(r), actualPath) {
                        case Some(params) => Some((r, params))
                        case None         => resolve(rest, m, actualPath)
                    }
                else
                    resolve(rest, m, actualPath)
        }
}
