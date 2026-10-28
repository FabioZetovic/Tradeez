import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { LandingPage } from './LandingPage'

describe('the landing page (T50)', () => {
  it('carries no badge over the headline and no step numbers (T50 D-1, D-5)', () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>,
    )
    expect(screen.queryByText(/ICT on real NQ and ES/i)).toBeNull()
    expect(screen.queryByText('01')).toBeNull()
  })
})
