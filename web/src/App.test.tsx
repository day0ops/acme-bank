import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('renders the Acme Bank landing placeholder at the root route', () => {
    render(<App />)
    // Scoped to the heading role: "Acme Bank" also appears in the nav brand link.
    expect(screen.getByRole('heading', { name: 'Acme Bank' })).toBeInTheDocument()
  })
})
