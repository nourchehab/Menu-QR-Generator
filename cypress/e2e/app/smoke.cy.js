describe('FlavorFrame public pages', () => {
  it('loads landing page', () => {
    cy.visit('/');
    cy.contains('h1', 'Menus, branches, and QR experiences that feel').should('be.visible');
    cy.contains('a', 'Log In').should('have.attr', 'href', '/login');
    cy.contains('a', 'Start Free').should('have.attr', 'href', '/signup');
  });

  it('loads login page and shows form fields', () => {
    cy.visit('/login');
    cy.contains('h2', 'Welcome Back!').should('be.visible');
    cy.get('input[name="username"]').should('be.visible');
    cy.get('input[name="password"]').should('be.visible');
    cy.get('button[type="submit"]').contains('Continue').should('be.visible');
  });

  it('loads signup page and shows create account form', () => {
    cy.visit('/signup');
    cy.contains('h2', 'Create Account').should('be.visible');
    cy.get('input[name="email"]').should('be.visible');
    cy.get('input[name="password"]').should('be.visible');
    cy.get('input[name="confirmPassword"]').should('be.visible');
  });
});
