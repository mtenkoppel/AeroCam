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

  /* ---------- hero: live REC timer ---------- */
  var recTime = document.getElementById("rec-time");
  var seconds = 12 * 60 + 36;
  if (recTime && !reducedMotion) {
    setInterval(function () {
      seconds += 1;
      var m = Math.floor(seconds / 60);
      var s = seconds % 60;
      recTime.textContent =
        (m < 10 ? "0" + m : m) + ":" + (s < 10 ? "0" + s : s);
    }, 1000);
  }

  /* ----------------------------------------------------------------------
     Hero: highlight feedback.
     Mirrors the real widget behavior (DjiWidgetRenderer FEEDBACK_COMPACT):
     when a highlight fires, "★ <trigger>" briefly replaces the recording
     timer on the red tile, then the timer returns. The star button flashes
     like a pressed surface.
     ---------------------------------------------------------------------- */
  var primary = document.getElementById("kvideo-primary");
  var feedbackEl = document.getElementById("kvideo-feedback");
  var starBtn = document.getElementById("kvideo-star");
  if (primary && feedbackEl && !reducedMotion) {
    var feedbacks = [
      "★ MAX SPEED 61 KM/H",
      "★ PEAK POWER 780 W",
      "★ CLIMB SUMMIT",
      "★ LAP 3",
      "★ HIGH-SPEED DESCENT",
      "★ PEAK HEART RATE 188 BPM"
    ];
    var idx = 0;
    var fireHighlight = function () {
      feedbackEl.textContent = feedbacks[idx % feedbacks.length];
      idx += 1;
      if (starBtn) starBtn.classList.add("flash");
      primary.classList.add("feedback");
      setTimeout(function () {
        primary.classList.remove("feedback");
        if (starBtn) starBtn.classList.remove("flash");
      }, 2200);
    };
    setTimeout(fireHighlight, 1400);
    setInterval(fireHighlight, 5200);
  }

  /* ---------- trigger board: sequential LED sweep ---------- */
  var triggers = document.querySelectorAll(".trigger");
  if (triggers.length && !reducedMotion) {
    var t = 0;
    setInterval(function () {
      triggers.forEach(function (el) { el.classList.remove("lit"); });
      triggers[t % triggers.length].classList.add("lit");
      t += 1;
    }, 1100);
  } else {
    triggers.forEach(function (el) { el.classList.add("lit"); });
  }

  /* ---------- keep-awake stat: count-up + battery drain 100% -> 64% ---------- */
  var statValue = document.getElementById("stat-count");
  var batteryFill = document.getElementById("battery-fill");
  if (statValue) {
    var target = parseInt(statValue.getAttribute("data-count"), 10);
    var animateStat = function () {
      if (reducedMotion) {
        statValue.textContent = String(target);
        if (batteryFill) batteryFill.style.width = "64%";
        return;
      }
      var start = null;
      var duration = 1600;
      var step = function (ts) {
        if (!start) start = ts;
        var p = Math.min((ts - start) / duration, 1);
        var eased = 1 - Math.pow(1 - p, 3);
        statValue.textContent = String(Math.round(eased * target));
        if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
      if (batteryFill) {
        requestAnimationFrame(function () { batteryFill.style.width = "64%"; });
      }
      /* rAF is suspended in unfocused/background pages — guarantee the end state */
      setTimeout(function () {
        statValue.textContent = String(target);
        if (batteryFill) batteryFill.style.width = "64%";
      }, duration + 400);
    };
    watch(statValue, animateStat);
  }
})();
