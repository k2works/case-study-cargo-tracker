import { render, screen } from '@testing-library/react'
import { BrowserRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect } from 'vitest'
import App from './App'

function renderWithProviders() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>,
  )
}

describe('App', () => {
  it('renders dashboard heading', () => {
    renderWithProviders()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })

  it('renders app name in header', () => {
    renderWithProviders()
    expect(screen.getByText('Cargo Tracker')).toBeInTheDocument()
  })
})
