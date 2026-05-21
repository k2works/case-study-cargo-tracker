import { Routes, Route, Link } from 'react-router-dom';

/**
 * 仮のルートコンポーネント。
 *
 * IT1 時点では基盤動作確認用の最小ルーティングのみ提供する。
 * 実機能（ログイン・荷主登録・予約・航海スケジュール）は IT2 以降で
 * features/ 配下に実装する。
 */
function Home() {
  return (
    <section>
      <h1>Cargo Tracker</h1>
      <p>国際貨物輸送管理システム — フロントエンド基盤構築 (IT1)</p>
      <nav>
        <ul>
          <li>
            <Link to="/about">About</Link>
          </li>
        </ul>
      </nav>
    </section>
  );
}

function About() {
  return (
    <section>
      <h1>About</h1>
      <p>本アプリは React 19 + Vite 6 + TypeScript で構築されています。</p>
      <Link to="/">Home に戻る</Link>
    </section>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/about" element={<About />} />
    </Routes>
  );
}
