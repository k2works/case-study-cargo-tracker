import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Pagination from './Pagination';

describe('Pagination', () => {
  it('IT2: 件数範囲を「X-Y / Z 件」形式で表示する', () => {
    render(<Pagination page={0} size={20} totalCount={47} onPageChange={() => {}} />);
    expect(screen.getByRole('status')).toHaveTextContent('1-20 / 47 件');
  });

  it('IT2: 最終ページの件数範囲は端数で計算される', () => {
    render(<Pagination page={2} size={20} totalCount={47} onPageChange={() => {}} />);
    expect(screen.getByRole('status')).toHaveTextContent('41-47 / 47 件');
  });

  it('IT2: 0 件のときは「0 件」と表示する', () => {
    render(<Pagination page={0} size={20} totalCount={0} onPageChange={() => {}} />);
    expect(screen.getByRole('status')).toHaveTextContent('0 件');
  });

  it('IT2: 先頭ページでは「前へ」が無効、「次へ」は有効', () => {
    render(<Pagination page={0} size={20} totalCount={47} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: '前へ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '次へ' })).toBeEnabled();
  });

  it('IT2: 最終ページでは「次へ」が無効、「前へ」は有効', () => {
    render(<Pagination page={2} size={20} totalCount={47} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '前へ' })).toBeEnabled();
  });

  it('IT2: 1 ページのみのときは前後とも無効', () => {
    render(<Pagination page={0} size={20} totalCount={5} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: '前へ' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled();
  });

  it('IT2: 次へクリックで onPageChange に page+1 が渡される', async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={1} size={20} totalCount={47} onPageChange={onPageChange} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '次へ' }));
    expect(onPageChange).toHaveBeenCalledWith(2);
  });

  it('IT2: 前へクリックで onPageChange に page-1 が渡される', async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={1} size={20} totalCount={47} onPageChange={onPageChange} />);
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: '前へ' }));
    expect(onPageChange).toHaveBeenCalledWith(0);
  });
});
