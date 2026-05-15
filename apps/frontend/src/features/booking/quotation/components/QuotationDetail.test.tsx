import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { QuotationDetail } from './QuotationDetail'
import type { QuotationResponse } from '../types/quotation'

const baseQuotation: QuotationResponse = {
  quotationId: 'Q-TEST-001',
  shipperId: 1,
  originUnLocode: 'JPTYO',
  destinationUnLocode: 'USNYC',
  arrivalDeadline: '2026-12-31',
  cargoType: 'GENERAL',
  weightKg: 100,
  estimatedAmount: 100000,
  estimatedCurrency: 'JPY',
  validUntil: '2026-08-31',
  status: 'OFFERED',
  hazardImoClass: null,
  hazardUnNumber: null,
  hazardDeclaration: null,
  candidates: [
    {
      candidateSeq: 1,
      estimatedDays: 14,
      estimatedCost: 100000,
      estimatedCurrency: 'JPY',
      itinerarySummary: 'JPTYO → USNYC',
      voyageNumbers: 'V-001',
    },
  ],
}

describe('QuotationDetail', () => {
  it('受入条件 2-3: 基本情報とルート候補テーブルが表示される', () => {
    render(<QuotationDetail quotation={baseQuotation} />)
    expect(screen.getByTestId('candidates-table')).toBeInTheDocument()
    expect(screen.getByText('JPTYO → USNYC')).toBeInTheDocument()
    expect(screen.getByText('14 日')).toBeInTheDocument()
    expect(screen.getByText('OFFERED')).toBeInTheDocument()
  })

  it('受入条件 5: DRAFT + 候補ゼロの場合「期限内ルートなし」警告を表示する', () => {
    render(<QuotationDetail quotation={{ ...baseQuotation, status: 'DRAFT', candidates: [] }} />)
    expect(screen.getByTestId('no-route-warning')).toBeInTheDocument()
    expect(screen.queryByTestId('candidates-table')).not.toBeInTheDocument()
  })

  it('受入条件 6: 危険物情報があるときは申告セクションを表示する', () => {
    render(
      <QuotationDetail
        quotation={{
          ...baseQuotation,
          cargoType: 'HAZARDOUS',
          hazardImoClass: 'Class 3',
          hazardUnNumber: 'UN1170',
          hazardDeclaration: 'ethanol',
        }}
      />,
    )
    expect(screen.getByText('Class 3')).toBeInTheDocument()
    expect(screen.getByText('UN1170')).toBeInTheDocument()
    expect(screen.getByText('ethanol')).toBeInTheDocument()
  })
})
