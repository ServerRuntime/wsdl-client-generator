// ── Kullanım Kılavuzu Modal ───────────────────────────
(function () {
    const overlay = document.getElementById('guideOverlay');
    const openBtn = document.getElementById('guideBtn');
    const closeBtn = document.getElementById('guideClose');
    if (!overlay || !openBtn) return;

    function open()  { overlay.classList.add('open'); document.body.style.overflow = 'hidden'; }
    function close() { overlay.classList.remove('open'); document.body.style.overflow = ''; }

    openBtn.addEventListener('click', open);
    closeBtn?.addEventListener('click', close);
    overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') close(); });
})();

// ── Theme toggle ──────────────────────────────────────
(function () {
    const toggle = document.getElementById('themeToggle');
    if (!toggle) return;
    toggle.addEventListener('click', () => {
        const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('theme', next);
    });
})();

// ── Dual tab switching (prod + test) ──────────────────
document.querySelectorAll('.wsdl-env-block').forEach(block => {
    const tabs    = block.querySelectorAll('.tab');
    const contents = block.querySelectorAll('.tab-content');

    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');

            const target = tab.dataset.tab;
            contents.forEach(c => {
                const show = c.id === 'tab-' + target;
                c.classList.toggle('hidden', !show);
            });
        });
    });
});

// ── File drop helper ──────────────────────────────────
function initFileDrop(dropId, inputId, selectedId, nameId) {
    const drop     = document.getElementById(dropId);
    const input    = document.getElementById(inputId);
    const selected = document.getElementById(selectedId);
    const nameEl   = document.getElementById(nameId);
    if (!drop || !input) return;

    function showFile(name) {
        drop.querySelector('.file-placeholder').style.display = 'none';
        selected.classList.remove('hidden');
        nameEl.textContent = name;
    }

    input.addEventListener('change', () => {
        if (input.files[0]) showFile(input.files[0].name);
    });

    drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('drag-over'); });
    drop.addEventListener('dragleave', ()  => drop.classList.remove('drag-over'));
    drop.addEventListener('drop', e => {
        e.preventDefault();
        drop.classList.remove('drag-over');
        const file = e.dataTransfer.files[0];
        if (!file) return;
        const dt = new DataTransfer();
        dt.items.add(file);
        input.files = dt.files;
        showFile(file.name);
    });
}

initFileDrop('fileDropProd', 'wsdlFileProd', 'fileSelectedProd', 'fileNameProd');
initFileDrop('fileDropTest', 'wsdlFileTest', 'fileSelectedTest', 'fileNameTest');

// ── Output preview — live update ──────────────────────
function updatePreview() {
    const artifactId = document.getElementById('artifactId')?.value || 'soap-client';
    const version    = document.getElementById('version')?.value    || '1.0.0';
    const pascal     = artifactId.split(/[-_.]/g)
        .map(p => p ? p[0].toUpperCase() + p.slice(1) : '')
        .join('');

    const el = id => document.getElementById(id);
    if (el('previewProdName')) el('previewProdName').textContent = `${artifactId}-Prod.wsdl`;
    if (el('previewTestName')) el('previewTestName').textContent = `${artifactId}-Test.wsdl`;
    if (el('previewBean'))     el('previewBean').textContent     = `${pascal}ServiceBean.java`;
    if (el('previewJar'))      el('previewJar').textContent      = `lib/${artifactId}-${version}-shaded.jar`;
}

document.getElementById('artifactId')?.addEventListener('input', updatePreview);
document.getElementById('version')?.addEventListener('input', updatePreview);
updatePreview();

// ── Form submit → loading state ────────────────────────
const form      = document.getElementById('genForm');
const submitBtn = document.getElementById('submitBtn');

if (form) {
    form.addEventListener('submit', () => {
        submitBtn.disabled = true;
        submitBtn.innerHTML = `
          <svg width="18" height="18" viewBox="0 0 18 18" class="spin-inline">
            <circle cx="9" cy="9" r="7" stroke="currentColor" stroke-width="1.5" fill="none" opacity=".3"/>
            <path d="M9 2a7 7 0 0 1 7 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          İşleniyor...`;
    });
}

const style = document.createElement('style');
style.textContent = `@keyframes spin-inline { to { transform: rotate(360deg); } } .spin-inline { animation: spin-inline .7s linear infinite; }`;
document.head.appendChild(style);
