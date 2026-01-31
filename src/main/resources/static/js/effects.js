/* =========================
   SCROLL-BASED UI POLISH
   ========================= */

const body = document.body;
const navbar = document.querySelector(".navbar");

window.addEventListener("scroll", () => {
    const scrollY = window.scrollY;

    /* BACKGROUND GRADIENT SHIFT */
    body.style.background = `
        linear-gradient(
            180deg,
            #fbf7f2 ${Math.min(40 + scrollY / 15, 75)}%,
            #ffffff
        )
    `;

    /* NAVBAR SHADOW ON SCROLL */
    if (navbar) {
        if (scrollY > 10) {
            navbar.classList.add("scrolled");
        } else {
            navbar.classList.remove("scrolled");
        }
    }
});
