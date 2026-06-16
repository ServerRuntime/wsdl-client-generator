// ── Theme toggle ──────────────────────────────────────
(function () {
    const toggle = document.getElementById('themeToggle');
    if (!toggle) return;
    toggle.addEventListener('click', () => {
        const current = document.documentElement.getAttribute('data-theme') || 'dark';
        const next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('theme', next);
    });
})();

// JOB_ID Thymeleaf tarafından inject edilir (status.html'de)
const POLL_INTERVAL_MS = 2000;

const STEP_ORDER = ['FETCHING_WSDL', 'GENERATING_CODE', 'BUILDING', 'PACKAGING', 'COMPLETED'];

const STEP_TITLES = {
  PENDING:         'İş Kuyruğa Alındı',
  FETCHING_WSDL:   'WSDL İndiriliyor',
  GENERATING_CODE: 'Kod Üretiliyor',
  BUILDING:        'Maven Build Çalışıyor',
  PACKAGING:       'ZIP Oluşturuluyor',
  COMPLETED:       'Tamamlandı!',
  FAILED:          'Hata Oluştu',
};

let pollTimer = null;

function poll() {
  fetch(`${API_BASE}${JOB_ID}`)
    .then(r => r.json())
    .then(render)
    .catch(err => console.error('Polling error:', err));
}

function render(data) {
  const { status, message, progress, downloadUrl, errorDetail } = data;

  // Progress bar
  document.getElementById('progressFill').style.width = progress + '%';
  document.getElementById('progressPct').textContent  = progress + '%';

  // Title & message
  document.getElementById('statusTitle').textContent = STEP_TITLES[status] || status;
  document.getElementById('statusMsg').textContent   = message || '';

  // Status icon
  const iconEl = document.getElementById('statusIcon');
  if (status === 'COMPLETED') {
    iconEl.innerHTML = `<svg width="52" height="52" viewBox="0 0 52 52">
      <circle cx="26" cy="26" r="25" stroke="#22c55e" stroke-width="1.5" fill="none"/>
      <path d="M16 26l7 7 13-13" stroke="#22c55e" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
    </svg>`;
  } else if (status === 'FAILED') {
    iconEl.innerHTML = `<svg width="52" height="52" viewBox="0 0 52 52">
      <circle cx="26" cy="26" r="25" stroke="#ef4444" stroke-width="1.5" fill="none"/>
      <path d="M18 18l16 16M34 18L18 34" stroke="#ef4444" stroke-width="2.5" stroke-linecap="round"/>
    </svg>`;
  } else {
    iconEl.innerHTML = '<div class="spinner"></div>';
  }

  // Pipeline dots
  const currentIdx = STEP_ORDER.indexOf(status);
  STEP_ORDER.forEach((step, i) => {
    const el = document.getElementById('pipe-' + step);
    if (!el) return;
    el.classList.remove('active', 'done', 'error');
    if (status === 'FAILED' && step === STEP_ORDER[currentIdx > 0 ? currentIdx - 1 : 0]) {
      el.classList.add('error');
    } else if (i < currentIdx) {
      el.classList.add('done');
    } else if (i === currentIdx) {
      el.classList.add('active');
    }
  });

  if (status === 'COMPLETED') {
    // Connector lines between done steps
    updateLines();
    document.getElementById('downloadSection').classList.remove('hidden');
    document.getElementById('downloadBtn').href = `${DOWN_BASE}${JOB_ID}/download`;
    stopPolling();
  } else if (status === 'FAILED') {
    document.getElementById('errorSection').classList.remove('hidden');
    document.getElementById('errorDetail').textContent = errorDetail || 'Bilinmeyen hata';
    stopPolling();
  }
}

function updateLines() {
  // Turn pipe-lines green when all steps are done
  document.querySelectorAll('.pipe-line').forEach(l => l.style.background = 'var(--green)');
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

// Hemen bir istek at, sonra periyodik olarak devam et
poll();
pollTimer = setInterval(poll, POLL_INTERVAL_MS);
