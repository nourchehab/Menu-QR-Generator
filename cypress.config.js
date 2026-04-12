module.exports = {
  projectId: "vsdma6",

  e2e: {
    baseUrl: "http://localhost:8081",
    specPattern: "cypress/e2e/app/**/*.cy.js",
    setupNodeEvents(on, config) {
      // implement node event listeners here
    },
  },
};
