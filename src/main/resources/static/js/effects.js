/* =========================
   SCROLL-BASED UI POLISH
   ========================= */
let scrollEffectsInitialized = false;

function initScrollEffects() {
    if (scrollEffectsInitialized) return;
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
    scrollEffectsInitialized = true;
}

// Ensure DOM is loaded before running scroll effects
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initScrollEffects);
} else {
    initScrollEffects();
}

/* ======= Global instrumentation & cleanup (diagnostic + mitigation) =======
   - Tracks PerformanceObserver instances, RAFs, intervals/timeouts
   - On pagehide/unload it disconnects/clears to avoid retaining Performance entries
   This is a safe, short-term mitigation while we patch sources creating observers/timers.
*/
(function(){
    // avoid double-install
    if (window.__cleanupInstrumentationInstalled) return;
    window.__cleanupInstrumentationInstalled = true;

    // Track observers
    window.__createdPerfObservers = window.__createdPerfObservers || [];
    const OrigPerfObserver = window.PerformanceObserver;
    if (typeof OrigPerfObserver === 'function') {
        function WrappedPerformanceObserver(cb){
            const inst = new OrigPerfObserver(cb);
            try { window.__createdPerfObservers.push(inst); } catch(e){}
            return inst;
        }
        WrappedPerformanceObserver.prototype = OrigPerfObserver.prototype;
        window.PerformanceObserver = WrappedPerformanceObserver;
    }

    // Track RAFs
    window.__rafIds = window.__rafIds || new Set();
    const origRAF = window.requestAnimationFrame.bind(window);
    const origCancelRAF = window.cancelAnimationFrame.bind(window);
    window.requestAnimationFrame = function(cb){
        const id = origRAF(function(t){ try{ cb(t); } finally { window.__rafIds.delete(id); }});
        try{ window.__rafIds.add(id); }catch(e){}
        return id;
    };
    window.cancelAnimationFrame = function(id){ try{ window.__rafIds.delete(id); }catch(e){}; return origCancelRAF(id); };

    // Track intervals/timeouts
    window.__timerIds = window.__timerIds || new Set();
    const origSetInterval = window.setInterval.bind(window);
    const origSetTimeout = window.setTimeout.bind(window);
    const origClearInterval = window.clearInterval.bind(window);
    const origClearTimeout = window.clearTimeout.bind(window);
    window.setInterval = function(fn, ms){ const id = origSetInterval(fn, ms); try{ window.__timerIds.add(id); }catch(e){}; return id; };
    window.setTimeout = function(fn, ms){ const id = origSetTimeout(fn, ms); try{ window.__timerIds.add(id); }catch(e){}; return id; };
    window.clearInterval = function(id){ try{ window.__timerIds.delete(id); }catch(e){}; return origClearInterval(id); };
    window.clearTimeout = function(id){ try{ window.__timerIds.delete(id); }catch(e){}; return origClearTimeout(id); };

    // Cleanup routine
    function runGlobalCleanup(){
        try{
            // disconnect observers
            (window.__createdPerfObservers || []).forEach(o => { try{ o.disconnect(); }catch(e){} });
            window.__createdPerfObservers = [];

            // cancel RAFs
            (window.__rafIds || new Set()).forEach(id => { try{ origCancelRAF(id); }catch(e){} });
            window.__rafIds = new Set();

            // clear timers
            (window.__timerIds || new Set()).forEach(id => { try{ origClearInterval(id); origClearTimeout(id); }catch(e){} });
            window.__timerIds = new Set();

            // stop tracked media streams if any
            (window.__activeMediaStreams || []).forEach(s => { try{ s.getTracks().forEach(t=>t.stop()); }catch(e){} });
            window.__activeMediaStreams = [];
        }catch(e){ /* swallow */ }
    }

    // Run cleanup on pagehide/unload to prevent large retained trees
    window.addEventListener('pagehide', runGlobalCleanup, {passive:true});
    window.addEventListener('beforeunload', runGlobalCleanup, {passive:true});
    // also on visibilitychange when hidden
    document.addEventListener('visibilitychange', function(){ if (document.visibilityState === 'hidden') runGlobalCleanup(); }, {passive:true});

    // expose helper to run manually from console
    window.__runGlobalCleanup = runGlobalCleanup;
})();
