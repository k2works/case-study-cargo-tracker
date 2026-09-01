import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../../stores/auth-store'
import { loginAs, renderWithProviders } from '../../test/render'
import App from '../../App'

/**
 * **認証の外にある画面でも、ログイン済みならメニューを出す。**
 *
 * <p>公開の追跡照会（US18-5）は認証の外に置いている。しかし業務利用者は
 * サイドバーの「貨物追跡」からここへ来る——そこでメニューが消えると、
 * **戻る手段がブラウザバックしか無くなる**。認証の外に置くことと、
 * ログイン済みの人にメニューを出さないことは別である。
 */
describe('認証の外にある画面のメニュー', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('ログイン済みで追跡照会を開くと、メニューが出たままになる', () => {
    loginAs(['ROLE_TRACKER'])

    renderWithProviders(<App />, ['/tracking'])

    expect(
      screen.queryByRole('navigation', { name: 'メインナビゲーション' }),
      'ログイン済みなのにメニューが消えている',
    ).not.toBeNull()
    expect(screen.getByRole('link', { name: 'ダッシュボード' })).toBeTruthy()
  })

  it('未ログインで追跡照会を開くと、メニューは出ない', () => {
    renderWithProviders(<App />, ['/tracking'])

    expect(
      screen.queryByRole('navigation', { name: 'メインナビゲーション' }),
      '荷主向けの公開画面に業務メニューが出ている',
    ).toBeNull()
  })
})

/**
 * **入れ子の `main` を作らない。**
 *
 * <p>共通レイアウトは本文を `main` に入れている。その中の画面がもう一度 `main` を
 * 置くと、支援技術には「本文が 2 つある」と読める。
 */
describe('共通レイアウトの中に置いたときの構造', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('ログイン済みで開いても、本文の領域は 1 つだけ', () => {
    loginAs(['ROLE_TRACKER'])

    renderWithProviders(<App />, ['/tracking'])

    expect(screen.getAllByRole('main'), '本文の領域が入れ子になっている').toHaveLength(1)
  })

  it('未ログインで開いても、本文の領域はある', () => {
    renderWithProviders(<App />, ['/tracking'])

    expect(screen.getAllByRole('main')).toHaveLength(1)
  })

  /**
   * **戻り先は、その人が来た場所にする。**
   *
   * <p>ログイン済みの利用者にとって「トップ」はポータル（未ログインの入口）ではなく
   * ダッシュボードである。ポータルへ送ると、もう一度ログインを求められたように見える。
   */
  it('ログイン済みなら、戻り先はダッシュボードになる', () => {
    loginAs(['ROLE_TRACKER'])

    renderWithProviders(<App />, ['/tracking'])

    const back = screen.getByRole('link', { name: 'ダッシュボードに戻る' })
    expect(back.getAttribute('href')).toBe('/dashboard')
  })

  it('未ログインなら、戻り先はトップのままになる', () => {
    renderWithProviders(<App />, ['/tracking'])

    const back = screen.getByRole('link', { name: 'トップに戻る' })
    expect(back.getAttribute('href')).toBe('/')
  })
})
