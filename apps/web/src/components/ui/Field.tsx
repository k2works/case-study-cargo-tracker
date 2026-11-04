import { useId, type InputHTMLAttributes } from 'react';

/** ラベル・入力・項目ごとのエラー表示。エラーは `aria-describedby` と `aria-invalid` で入力に結びつける。 */
export function Field({ label, error, ...input }: { label: string; error?: string | undefined } & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  const errorId = `${id}-error`;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? errorId : undefined} {...input} />
      {error && (
        <p id={errorId} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}
