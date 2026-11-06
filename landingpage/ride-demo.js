(() => {
  const demo = document.querySelector('[data-ride-demo]');
  if (!demo) return;

  const cruise = { speed: 28, watts: 165, grade: 0, cadence: 84, tilt: 0, reach: 0, drop: 0, lift: 0, forward: 0 };
  const moves = {
    normal: cruise,
    uphill: { speed: 19, watts: 290, grade: 8, cadence: 48, tilt: -8, reach: 8, drop: -9, lift: 11, forward: 15 },
    downhill: { speed: 49, watts: 190, grade: -6, cadence: 102, tilt: 7, reach: 14, drop: 11, lift: 0, forward: 0 },
    attack: { speed: 37, watts: 352, grade: 0, cadence: 113, tilt: 0, reach: 10, drop: 4, lift: 7, forward: 0 },
  };
  const readouts = Object.fromEntries(
    ['speed', 'watts', 'grade'].map(key => [key, demo.querySelector(`[data-${key}]`)]),
  );
  const status = demo.querySelector('.ride-status');
  const armed = demo.querySelector('.ride-armed');
  const recording = demo.querySelector('.ride-recording');
  const video = demo.querySelector('.ride-video');
  const photo = demo.querySelector('.ride-photo');
  const star = demo.querySelector('.ride-star');
  const timer = demo.querySelector('[data-timer]');
  const label = demo.querySelector('[data-rec-label]');
  const ground = demo.querySelector('[data-ground]');
  const sprite = demo.querySelector('[data-sprite]');
  const assembly = demo.querySelector('[data-assembly]');
  const wheels = demo.querySelectorAll('[data-wheel]');
  const [body, head, far, near, crank] = ['body', 'head', 'far', 'near', 'crank']
    .map(name => demo.querySelector(`[data-${name}]`));
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const buttons = demo.querySelectorAll('[data-move]');
  const modePicker = demo.closest('section').querySelector('.ride-mode');

  const rampMs = 1800;
  const attackMs = 4750;
  const quietMs = 60_000;
  const quietPlaybackRate = 12;
  let pose = { ...cruise };
  let transition = null;
  let selectedMove = 'normal';
  let attackStarted = null;
  let attackFinished = false;
  let lastFrame = null;
  let phase = .45;
  let wheelAngle = 0;
  let active = { speed: false, watts: false, grade: false };
  let elapsed = null;
  let automatic = false;
  let photoUntil = -Infinity;
  let highlightUntil = -Infinity;
  let quiet = 0;
  let startedAt = 0;
  let stoppedAt = -Infinity;
  let reason = '';

  function transitionTo(target, now) {
    transition = { from: pose, to: target, at: now };
  }

  function selectMove(key, now) {
    if (key === 'uphill' && selectedMove === 'uphill') key = 'normal';
    selectedMove = key;
    transitionTo(moves[key], now);
    attackStarted = key === 'attack' ? now : null;
    attackFinished = false;
    for (const button of buttons) button.setAttribute('aria-pressed', String(button.dataset.move === key));
  }

  function updatePose(now) {
    if (attackStarted !== null && !attackFinished && now - attackStarted >= attackMs) {
      attackFinished = true;
      transitionTo(cruise, now);
    }
    if (!transition) return;
    const t = reducedMotion.matches ? 1 : Math.min(1, (now - transition.at) / rampMs);
    const eased = t * t * (3 - 2 * t);
    pose = Object.fromEntries(Object.keys(cruise).map(key =>
      [key, transition.from[key] + (transition.to[key] - transition.from[key]) * eased]));
    if (t === 1) transition = null;
  }

  function startRecording(now, trigger = '') {
    elapsed = 0;
    quiet = 0;
    startedAt = now;
    automatic = Boolean(trigger);
    reason = trigger;
  }

  function stopRecording(now) {
    elapsed = null;
    highlightUntil = -Infinity;
    quiet = 0;
    stoppedAt = now;
  }

  function updateCapture(now, dt) {
    const next = {
      speed: pose.speed >= (active.speed ? 42 : 45),
      watts: pose.watts >= (active.watts ? 302 : 352),
      grade: pose.grade >= (active.grade ? 6 : 7),
    };
    if (elapsed === null && demo.dataset.mode !== 'manual') {
      const trigger = Object.keys(next).find(key => next[key] && !active[key]);
      if (trigger) {
        if (demo.dataset.mode === 'video') {
          startRecording(now, { speed: 'Speed trigger', watts: 'Power trigger', grade: 'Ascent trigger' }[trigger]);
        } else {
          capturePhoto(now);
        }
      }
    }
    active = next;
    if (elapsed === null) return;
    if (!automatic) {
      elapsed += dt;
      return;
    }
    const triggered = Object.values(active).some(Boolean);
    const step = dt * (triggered ? 1 : quietPlaybackRate);
    elapsed += step;
    quiet = triggered ? 0 : quiet + step;
    if (quiet >= quietMs) stopRecording(now);
  }

  function capturePhoto(now) {
    photoUntil = now + 1500;
  }

  // Intersect two circles to keep thigh and shin lengths fixed.
  // The forward intersection puts the knee ahead of the hip-to-ankle line.
  function leg(hipX, hipY, pedalX, pedalY) {
    const ankleX = pedalX - 5;
    const ankleY = pedalY - 3;
    const thigh = 46;
    const shin = 46;
    const dx = ankleX - hipX;
    const dy = ankleY - hipY;
    const distance = Math.hypot(dx, dy);
    const along = (thigh * thigh - shin * shin + distance * distance) / (2 * distance);
    const across = Math.sqrt(Math.max(0, thigh * thigh - along * along));
    const kneeX = hipX + (dx * along + dy * across) / distance;
    const kneeY = hipY + (dy * along - dx * across) / distance;
    return `M${hipX} ${hipY}L${kneeX} ${kneeY}L${ankleX} ${ankleY}l10 3`;
  }

  function drawCyclist(now, dt) {
    const { tilt, reach, drop, lift, forward, cadence, speed } = pose;
    if (!reducedMotion.matches) {
      phase = (phase + dt * cadence * Math.PI / 30_000) % (2 * Math.PI);
      wheelAngle = (wheelAngle + dt * speed * .018) % 360;
    }

    const progress = attackStarted === null ? 0 : Math.min(1, (now - attackStarted) / attackMs);
    const offset = 212 * (reducedMotion.matches ? Number(attackFinished) : progress ** 1.65);
    sprite.setAttribute('transform', `translate(${offset} 0)`);
    ground.setAttribute('transform', `rotate(${tilt} 142 119)`);
    const hipX = 110 + lift * .65 + forward;
    const hipY = 67 - lift + Math.sin(phase * 2) * .6;
    const shoulderX = 141 + reach;
    const shoulderY = 41 + drop;
    const x = Math.cos(phase) * 13;
    const y = Math.sin(phase) * 13;
    assembly.setAttribute('transform', `rotate(${tilt} 142 119)`);
    for (const wheel of wheels) {
      wheel.setAttribute('transform', `rotate(${wheelAngle} ${wheel.dataset.wheel} 129)`);
    }
    far.setAttribute('d', leg(hipX, hipY, 133 - x, 136 - y));
    near.setAttribute('d', leg(hipX, hipY, 133 + x, 136 + y));
    crank.setAttribute('d', `M${133 - x} ${136 - y}L${133 + x} ${136 + y}`);
    body.setAttribute('d', `M${hipX} ${hipY}L${shoulderX} ${shoulderY}L${(shoulderX + 193) / 2 - 3} ${(shoulderY + 67) / 2 + 7}L193 67`);
    head.setAttribute('transform', `translate(${shoulderX + 10} ${shoulderY - 15})`);
  }

  function render(now) {
    const speed = pose.speed.toFixed(1);
    readouts.speed.textContent = speed;
    readouts.watts.textContent = Math.round(pose.watts);
    const grade = Math.round(pose.grade);
    readouts.grade.textContent = grade > 0 ? `+${grade}` : grade;

    const isRecording = elapsed !== null;
    const highlightAdded = isRecording && now < highlightUntil;
    const justStopped = !isRecording && now - stoppedAt < 1400;
    const photoTaken = !isRecording && now < photoUntil;
    const videoLabel = isRecording ? 'Stop recording' : 'Start recording';
    if (video.getAttribute('aria-label') !== videoLabel) video.setAttribute('aria-label', videoLabel);
    photo.toggleAttribute('data-captured', photoTaken);
    demo.toggleAttribute('data-recording', isRecording);
    armed.hidden = isRecording || justStopped;
    recording.hidden = !isRecording && !justStopped;
    timer.hidden = !isRecording;
    photo.hidden = isRecording;
    star.hidden = !isRecording;
    if (isRecording) {
      const seconds = Math.floor(elapsed / 1000);
      timer.textContent = [Math.floor(seconds / 60), seconds % 60].map(n => String(n).padStart(2, '0')).join(':');
    }
    label.hidden = isRecording && !automatic && !highlightAdded;
    label.textContent = justStopped ? 'REC STOPPED' : highlightAdded ? '★ HIGHLIGHT'
      : now - startedAt < 900 ? 'REC STARTED' : 'AUTO REC';
    const idleMessage = { video: 'Auto video armed', photo: 'Auto photo armed', manual: 'Manual capture' }[demo.dataset.mode];
    const message = highlightAdded ? 'Highlight added' : isRecording
      ? (!automatic ? 'Recording' : quiet > 0 ? 'Recording · quiet minute sped up' : `${reason} · recording`)
      : photoTaken ? 'Photo captured' : justStopped ? 'Recording stopped' : idleMessage;
    if (status.textContent !== message) status.textContent = message;
  }

  function tick(now) {
    const dt = lastFrame === null ? 0 : Math.min(64, now - lastFrame);
    lastFrame = now;
    updatePose(now);
    updateCapture(now, dt);
    drawCyclist(now, dt);
    render(now);
    requestAnimationFrame(tick);
  }

  video.addEventListener('click', () => {
    const now = performance.now();
    if (elapsed === null) startRecording(now);
    else stopRecording(now);
    render(now);
  });

  photo.addEventListener('click', () => {
    if (elapsed !== null) return;
    const now = performance.now();
    capturePhoto(now);
    render(now);
  });

  star.addEventListener('click', () => {
    if (elapsed === null) return;
    const now = performance.now();
    highlightUntil = now + 5000;
    render(now);
  });

  modePicker.addEventListener('change', event => {
    demo.dataset.mode = event.target.value;
    // Leaving Auto video hands an active clip back to the rider.
    if (demo.dataset.mode !== 'video') automatic = false;
    render(performance.now());
  });

  demo.dataset.mode = modePicker.querySelector(':checked').value;

  for (const button of buttons) {
    button.addEventListener('click', () => {
      selectMove(button.dataset.move, performance.now());
    });
  }
  requestAnimationFrame(tick);
})();
