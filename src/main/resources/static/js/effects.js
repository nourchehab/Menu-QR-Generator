/* =========================
   SCROLL-BASED UI POLISH
   ========================= */

function initScrollEffects() {
    const body = document.body;
    const navbar = document.querySelector(".navbar");
    let ticking = false;

    function updateOnScroll() {
        const scrollY = window.scrollY;

        /* BACKGROUND GRADIENT SHIFT */
        body.style.background = `linear-gradient(180deg, #fbf7f2 ${Math.min(40 + scrollY / 15, 75)}%, #ffffff)`;

        /* NAVBAR SHADOW ON SCROLL */
        if (navbar) {
            navbar.classList.toggle("scrolled", scrollY > 10);
        }
    }

    function onScroll() {
        if (!ticking) {
            window.requestAnimationFrame(() => {
                updateOnScroll();
                ticking = false;
            });
            ticking = true;
        }
    }

    window.addEventListener("scroll", onScroll, { passive: true });
}

// Ensure DOM is loaded before running scroll effects
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initScrollEffects);
} else {
    initScrollEffects();
}
