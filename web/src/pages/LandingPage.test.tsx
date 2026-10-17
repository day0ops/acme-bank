import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { LandingPage } from './LandingPage'
import { SandboxDashboardPage } from './SandboxDashboardPage'

describe('SandboxDashboardPage', () => {
  it('renders seeded data without making any network calls', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')

    render(<SandboxDashboardPage />)

    expect(screen.getByText('Welcome, Jordan')).toBeInTheDocument()
    expect(fetchSpy).not.toHaveBeenCalled()

    fetchSpy.mockRestore()
  })
})

describe('LandingPage', () => {
  it('renders the hero heading and all three calls to action, with no network calls', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')

    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )

    const heading = screen.getByRole('heading', { level: 1 })
    expect(heading).toHaveTextContent('Banking with')
    expect(heading).toHaveTextContent('the lights on.')
    expect(screen.getAllByRole('link', { name: 'Open an account' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Log in' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Try the live demo' }).length).toBeGreaterThan(0)
    expect(fetchSpy).not.toHaveBeenCalled()

    fetchSpy.mockRestore()
  })
})
