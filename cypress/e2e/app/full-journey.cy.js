const E2E_EMAIL = String(Cypress.env('E2E_EMAIL') || '').trim()
const E2E_PASSWORD = String(Cypress.env('E2E_PASSWORD') || '').trim()

function requireAuthVars() {
  if (!E2E_EMAIL || !E2E_PASSWORD) {
    throw new Error(
      `Missing Cypress auth vars. Provide E2E_EMAIL and E2E_PASSWORD via either ` +
      `CYPRESS_E2E_EMAIL/CYPRESS_E2E_PASSWORD shell vars or --env E2E_EMAIL=...,E2E_PASSWORD=.... ` +
      `Example: CYPRESS_E2E_EMAIL="you@example.com" CYPRESS_E2E_PASSWORD="your-password" ` +
      `npx cypress run --e2e --spec cypress/e2e/app/full-journey.cy.js`
    )
  }
}

function loginByFormPost() {
  return cy.request({
    method: 'POST',
    url: '/login',
    form: true,
    failOnStatusCode: false,
    body: {
      username: E2E_EMAIL,
      password: E2E_PASSWORD,
    },
  }).then((resp) => {
    expect([200, 302]).to.include(resp.status)
  })
}

function getDashboardData() {
  return cy.request({
    method: 'GET',
    url: '/admin/api/restaurants/dashboard',
    failOnStatusCode: false,
    followRedirect: false,
    headers: {
      Accept: 'application/json',
    },
  })
}

function ensureDashboardJson(resp) {
  if (resp.status === 301 || resp.status === 302) {
    throw new Error(`Dashboard API redirected (${resp.status}) to login; auth session was not established.`)
  }

  const body = resp.body
  if (typeof body === 'string' && /<html|<\!DOCTYPE html>/i.test(body)) {
    throw new Error('Dashboard API returned HTML login page instead of JSON; check E2E credentials/session.')
  }
}

function ensureRestaurantSetup() {
  return getDashboardData().then((resp) => {
    ensureDashboardJson(resp)

    const body = resp.body || {}
    const dto = body.data && typeof body.data === 'object' ? body.data : body

    // Some environments return { success, data }, others return the DTO directly.
    if (resp.status === 200 && dto && typeof dto === 'object' && dto.id) {
      return
    }

    return cy.request({
      method: 'POST',
      url: '/signup/restaurant/setup',
      form: true,
      failOnStatusCode: false,
      body: {
        restaurantName: `E2E Resto ${Date.now()}`,
        restaurantType: 'Cafe',
      },
    }).then((setupResp) => {
      expect([200, 201]).to.include(setupResp.status)
    })
  })
}

function pickBranchFromDashboard() {
  return getDashboardData().then((resp) => {
    ensureDashboardJson(resp)

    expect(resp.status).to.eq(200)
    const body = resp.body || {}
    if (Object.prototype.hasOwnProperty.call(body, 'success')) {
      expect(body.success).to.eq(true)
    }

    const dto = body.data && typeof body.data === 'object' ? body.data : body
    expect(dto).to.be.an('object')
    const branches = dto.branchDTOs || dto.branches || []
    expect(branches.length).to.be.greaterThan(0)

    // Prefer non-main branch when available.
    const target = branches.find((b) => !b.mainBranch && !b.isMainBranch) || branches[0]
    return {
      restaurantId: dto.id,
      branchId: target.id,
      branchName: target.branchName || 'Branch',
    }
  })
}

describe('FlavorFrame full user journey', () => {
  let restoreBranchId = null
  let restoreColor = null
  let changedTheme = false

  before(() => {
    requireAuthVars()
  })

  beforeEach(() => {
    restoreBranchId = null
    restoreColor = null
    changedTheme = false

    cy.session([E2E_EMAIL], () => {
      loginByFormPost()
    })
  })

  afterEach(() => {
    if (!changedTheme || !restoreBranchId || !restoreColor) {
      return
    }

    cy.request({
      method: 'PUT',
      url: `/api/restaurant/branch/${restoreBranchId}/theme`,
      failOnStatusCode: false,
      headers: { 'Content-Type': 'application/json' },
      body: { menuBackgroundColor: restoreColor },
    }).then((resp) => {
      expect(resp.status).to.eq(200)
    })
  })

  it('executes branch/menu/ai/theme/preview flow with core 200 checks', () => {
    let ctx
    let oldColor = null
    const newColor = '#123456'

    ensureRestaurantSetup()

    // Ensure there is an extra branch to work with.
    cy.request({
      method: 'POST',
      url: '/admin/api/branches',
      failOnStatusCode: false,
      headers: { 'Content-Type': 'application/json' },
      body: { branchName: `E2E Branch ${Date.now()}` },
    }).then((resp) => {
      expect([200, 201, 409, 500]).to.include(resp.status)
    })

    pickBranchFromDashboard().then((picked) => {
      ctx = picked
    })

    cy.then(() => {
      // Add two items.
      return cy.request({
        method: 'POST',
        url: `/api/branch/${ctx.branchId}/items`,
        form: true,
        body: {
          itemName: `E2E Item A ${Date.now()}`,
          itemPrice: '12.50',
          itemDescription: 'End-to-end test item A',
          category: 'Mains',
        },
      }).its('status').should('eq', 200)
    })

    cy.then(() => {
      return cy.request({
        method: 'POST',
        url: `/api/branch/${ctx.branchId}/items`,
        form: true,
        body: {
          itemName: `E2E Item B ${Date.now()}`,
          itemPrice: '8.00',
          itemDescription: 'End-to-end test item B',
          category: 'Drinks',
        },
      }).its('status').should('eq', 200)
    })

    // Delete one item if at least 3 exist, as requested.
    cy.then(() => {
      return cy.request(`/api/branch/${ctx.branchId}/items`).then((itemsResp) => {
        expect(itemsResp.status).to.eq(200)
        const items = itemsResp.body || []
        if (items.length >= 3) {
          const id = items[0].branchItemId || items[0].restaurantItemId || items[0].id
          return cy.request({
            method: 'DELETE',
            url: `/api/branch/${ctx.branchId}/items/${id}`,
          }).its('status').should('eq', 200)
        }
      })
    })

    // Manage page interactions: AI ideas + categorization.
    cy.intercept('POST', '/api/menu-ideas/generate').as('aiIdeasGenerate')
    cy.intercept('POST', /\/api\/restaurants\/\d+\/batch-categorize.*/).as('batchCategorize')

    cy.then(() => {
      cy.visit(`/manageitems?branchId=${ctx.branchId}&branchName=${encodeURIComponent(ctx.branchName)}`)
      cy.get('#aiIdeasBtn').should('be.visible')
      cy.get('#categoriseAllBtn').should('be.visible')
    })

    cy.get('#aiIdeasBtn').click()
    cy.get('#aiCuisineType').clear().type('Lebanese')
    cy.get('#aiRestaurantType').clear().type('Cafe')
    cy.get('#aiIdeasCount').clear().type('2')
    cy.get('#generateIdeasBtn').click()

    cy.wait('@aiIdeasGenerate').then((i) => {
      expect(i.response).to.exist
      expect([200, 503]).to.include(i.response.statusCode)
    })

    // If ideas are returned, add at least one.
    cy.get('body').then(($body) => {
      if ($body.find('#aiIdeasList input[type="checkbox"]').length > 0) {
        cy.get('#aiIdeasList input[type="checkbox"]').first().check({ force: true })
        cy.contains('button', 'Add Selected').click({ force: true })
      }
    })

    // Ensure ideas modal is closed so it does not cover categorization controls.
    cy.window().then((win) => {
      if (typeof win.closeAiIdeasModal === 'function') {
        win.closeAiIdeasModal()
      }
    })
    cy.get('#aiIdeasModal').should('not.be.visible')

    cy.get('#categoriseAllBtn').click()
    cy.get('#categoriseSubmitBtn').click()

    cy.wait('@batchCategorize', { timeout: 120000 }).then((i) => {
      expect(i.response).to.exist
      expect(i.response.statusCode).to.eq(200)
    })

    // Apply first suggested category if available.
    cy.get('body').then(($body) => {
      const applyButtons = $body.find('button:contains("Accept & Save")')
      if (applyButtons.length > 0) {
        cy.contains('button', 'Accept & Save').first().click({ force: true })
      }
    })

    // Track color before change.
    cy.then(() => {
      return cy.request(`/api/restaurant/branch/${ctx.branchId}`).then((resp) => {
        expect(resp.status).to.eq(200)
        oldColor = (resp.body && resp.body.menuBackgroundColor) || null
        restoreBranchId = ctx.branchId
        restoreColor = oldColor
      })
    })

    // Preview and category filter buttons.
    cy.then(() => {
      cy.visit(`/menu/preview?branchId=${ctx.branchId}`)
      cy.get('#categoryFilters', { timeout: 20000 }).should('be.visible')
      cy.get('#filterButtons .category-filter-btn').then(($btns) => {
        if ($btns.length > 0) {
          cy.wrap($btns[0]).click()
          cy.get('#clearFiltersBtn').click()
        }
      })
      cy.get('#google_translate_element').should('exist')
    })

    // Change theme color.
    cy.intercept('PUT', /\/api\/restaurant\/branch\/\d+\/theme/).as('saveTheme')
    cy.then(() => {
      cy.visit(`/menu/theme?branchId=${ctx.branchId}`)
      cy.get('#colorPicker').invoke('val', newColor).trigger('input').trigger('change')
      cy.get('#hexInput').invoke('val', newColor).trigger('input').trigger('change')
      cy.get('#hexInput').should('have.value', newColor)
      cy.get('#saveBtn').click()
    })

    cy.get('#saveMsg', { timeout: 20000 }).should('contain.text', 'Saved')

    // Source-of-truth check: verify persisted theme color via API.
    cy.then(() => {
      return cy.request(`/api/restaurant/branch/${ctx.branchId}`).then((resp) => {
        expect(resp.status).to.eq(200)
        const saved = String((resp.body && resp.body.menuBackgroundColor) || '').trim().toUpperCase()
        expect(saved).to.eq(newColor.toUpperCase())
        changedTheme = true
        if (oldColor && String(oldColor).trim().toUpperCase() !== newColor.toUpperCase()) {
          expect(saved).to.not.eq(String(oldColor).trim().toUpperCase())
        }
      })
    })

    // Verify preview applies the new color after its async data fetch completes.
    cy.intercept('GET', /\/api\/restaurant\/branch\/\d+/).as('previewRestaurant')
    cy.then(() => {
      cy.visit(`/menu/preview?branchId=${ctx.branchId}`)
      cy.wait('@previewRestaurant')
      cy.document().then((doc) => {
        const current = doc.documentElement.style.getPropertyValue('--menu-bg').trim().toUpperCase()
        expect(current).to.eq(newColor.toUpperCase())
      })
    })
  })
})
