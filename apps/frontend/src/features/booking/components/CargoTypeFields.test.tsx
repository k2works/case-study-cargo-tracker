import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import { CargoTypeFields } from './CargoTypeFields'
import type { CargoType, HazardInfo, TemperatureCondition } from '../types/booking'

interface SetupOptions {
  cargoType?: CargoType
  hazardInfo?: HazardInfo
  temperatureCondition?: TemperatureCondition
}

function setup(options: SetupOptions = {}) {
  const onHazardChange = vi.fn()
  const onTemperatureChange = vi.fn()
  render(
    <CargoTypeFields
      cargoType={options.cargoType ?? 'GENERAL'}
      hazardInfo={options.hazardInfo}
      temperatureCondition={options.temperatureCondition}
      onHazardChange={onHazardChange}
      onTemperatureChange={onTemperatureChange}
    />,
  )
  return { onHazardChange, onTemperatureChange }
}

describe('CargoTypeFields', () => {
  it('GENERAL のときは追加フィールドを表示しない', () => {
    setup({ cargoType: 'GENERAL' })
    expect(screen.queryByLabelText('IMO クラス')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('最低温度（℃）')).not.toBeInTheDocument()
  })

  it('HAZARDOUS のときは HazardInfo の入力欄を表示する', () => {
    setup({ cargoType: 'HAZARDOUS' })
    expect(screen.getByLabelText('IMO クラス')).toBeInTheDocument()
    expect(screen.getByLabelText('UN 番号')).toBeInTheDocument()
    expect(screen.getByLabelText('危険物宣言')).toBeInTheDocument()
  })

  it('REFRIGERATED のときは TemperatureCondition の入力欄を表示する', () => {
    setup({ cargoType: 'REFRIGERATED' })
    expect(screen.getByLabelText('最低温度（℃）')).toBeInTheDocument()
    expect(screen.getByLabelText('最高温度（℃）')).toBeInTheDocument()
  })

  it('HAZARDOUS から REFRIGERATED に切り替えると HazardInfo は表示されなくなる', () => {
    const { rerender } = render(
      <CargoTypeFields
        cargoType="HAZARDOUS"
        onHazardChange={vi.fn()}
        onTemperatureChange={vi.fn()}
      />,
    )
    expect(screen.getByLabelText('IMO クラス')).toBeInTheDocument()
    rerender(
      <CargoTypeFields
        cargoType="REFRIGERATED"
        onHazardChange={vi.fn()}
        onTemperatureChange={vi.fn()}
      />,
    )
    expect(screen.queryByLabelText('IMO クラス')).not.toBeInTheDocument()
    expect(screen.getByLabelText('最低温度（℃）')).toBeInTheDocument()
  })

  it('HazardInfo を入力すると onHazardChange が呼び出される', async () => {
    const user = userEvent.setup()
    const { onHazardChange } = setup({ cargoType: 'HAZARDOUS' })

    await user.type(screen.getByLabelText('IMO クラス'), '3')

    expect(onHazardChange).toHaveBeenCalled()
  })

  it('TemperatureCondition を入力すると onTemperatureChange が呼び出される', async () => {
    const user = userEvent.setup()
    const { onTemperatureChange } = setup({ cargoType: 'REFRIGERATED' })

    await user.type(screen.getByLabelText('最低温度（℃）'), '-10')

    expect(onTemperatureChange).toHaveBeenCalled()
  })
})
