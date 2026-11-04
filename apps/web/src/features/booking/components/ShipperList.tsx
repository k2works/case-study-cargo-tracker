import { useState } from 'react';
import { Link } from 'react-router';
import { QueryState } from '@/components/ui/QueryState';
import { useShippers } from '../api/hooks';

const KIND_LABELS: Record<string, string> = { INDIVIDUAL: '個人', CORPORATE: '法人' };

/** SC-20 荷主一覧・検索（個人情報を含む。営業担当者だけ）。 */
export function ShipperList() {
  const [keyword, setKeyword] = useState('');
  const shippers = useShippers(keyword);
  return (
    <section>
      <div className="field">
        <label htmlFor="keyword">検索（氏名・メール）</label>
        <input id="keyword" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
      </div>
      <QueryState isPending={shippers.isPending} error={shippers.error}>
        {shippers.data && shippers.data.length === 0 ? (
          <p>該当する荷主はいません</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th scope="col">氏名または社名</th>
                <th scope="col">種別</th>
                <th scope="col">メールアドレス</th>
              </tr>
            </thead>
            <tbody>
              {shippers.data?.map((s) => (
                <tr key={s.id}>
                  <td>
                    <Link to={`/shippers/${s.id}`}>{s.name}</Link>
                  </td>
                  <td>{KIND_LABELS[s.kind] ?? '不明'}</td>
                  <td>{s.email}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </QueryState>
    </section>
  );
}
