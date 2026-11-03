/* AeroCam landing — progressive enhancement only; page is fully usable without JS. */
(function () {
  "use strict";

  var reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- viewport watcher (scroll-based; IntersectionObserver is
     throttled in some embedded webviews, so keep it simple) ---------- */
  var watchers = [];
  function inView(el) {
    var r = el.getBoundingClientRect();
    return r.top < window.innerHeight * 0.9 && r.bottom > 0;
  }
  function watch(el, cb) {
    if (inView(el)) { cb(); return; }
    watchers.push({ el: el, cb: cb });
  }
  function checkWatchers() {
    for (var i = watchers.length - 1; i >= 0; i--) {
      if (inView(watchers[i].el)) {
        var w = watchers.splice(i, 1)[0];
        w.cb();
      }
    }
  }
  window.addEventListener("scroll", checkWatchers, { passive: true });
  window.addEventListener("resize", checkWatchers);

  /* ---------- scroll reveal ---------- */
  document.querySelectorAll(".reveal").forEach(function (el) {
    if (reducedMotion) { el.classList.add("visible"); return; }
    watch(el, function () { el.classList.add("visible"); });
  });

  /* ---------- thanks band: live GitHub star count (no backend needed) ---------- */
  var starCount = document.getElementById("star-count");
  if (starCount && window.fetch) {
    fetch("https://api.github.com/repos/mtenkoppel/AeroCam")
      .then(function (r) { return r.ok ? r.json() : null; })
      .then(function (data) {
        if (data && typeof data.stargazers_count === "number") {
          starCount.textContent = "· " + data.stargazers_count;
        }
      })
      .catch(function () { /* rate-limited or offline — button works without it */ });
  }
})();
