(function () {
    const dropArea   = document.getElementById('jar2wsdlDrop');
    const fileInput  = document.getElementById('jar2wsdlFileInput');
    const loading    = document.getElementById('jar2wsdlLoading');
    const loadingTxt = document.getElementById('jar2wsdlLoadingText');
    const errorBox   = document.getElementById('jar2wsdlError');
    const resultBox  = document.getElementById('jar2wsdlResult');
    const badge      = document.getElementById('jar2wsdlBadge');
    const filename   = document.getElementById('jar2wsdlFilename');
    const wsdlList   = document.getElementById('jar2wsdlList');
    const dlAllBtn   = document.getElementById('jar2wsdlDownloadAllBtn');
    const resetBtn   = document.getElementById('jar2wsdlResetBtn');

    let currentJobId = null;

    // ── Drag & drop ──────────────────────────────────────
    dropArea.addEventListener('dragover', e => {
        e.preventDefault();
        dropArea.classList.add('drag-over');
    });
    dropArea.addEventListener('dragleave', () => dropArea.classList.remove('drag-over'));
    dropArea.addEventListener('drop', e => {
        e.preventDefault();
        dropArea.classList.remove('drag-over');
        const file = e.dataTransfer.files[0];
        if (file) processJar(file);
    });

    fileInput.addEventListener('change', () => {
        if (fileInput.files[0]) processJar(fileInput.files[0]);
    });

    resetBtn.addEventListener('click', reset);

    // ── İşlem ────────────────────────────────────────────
    async function processJar(file) {
        if (!file.name.toLowerCase().endsWith('.jar')) {
            showError('Lütfen geçerli bir .jar dosyası seçin.');
            return;
        }
        if (file.size > 50 * 1024 * 1024) {
            showError('Dosya boyutu 50 MB\'ı aşıyor.');
            return;
        }

        hide(dropArea); hide(errorBox); hide(resultBox);
        show(loading);
        loadingTxt.textContent = file.name + ' taranıyor…';

        try {
            const fd = new FormData();
            fd.append('jar', file);
            const res  = await fetch('jar2wsdl/extract', { method: 'POST', body: fd });
            const data = await res.json();
            if (!res.ok) throw new Error(data.message || 'Sunucu hatası');

            currentJobId = data.jobId;
            const entries = data.entries || [];

            hide(loading);
            renderResult(file.name, entries);
        } catch (err) {
            hide(loading);
            show(dropArea);
            showError(err.message);
        }
    }

    function renderResult(jarName, entries) {
        wsdlList.innerHTML = '';

        filename.textContent = jarName;
        if (entries.length === 0) {
            badge.textContent = 'WSDL bulunamadı';
            badge.className = 'jar2wsdl-result-badge badge-warn';
            dlAllBtn.style.display = 'none';
        } else {
            badge.textContent = entries.length + ' WSDL bulundu';
            badge.className = 'jar2wsdl-result-badge badge-ok';
            dlAllBtn.style.display = entries.length > 1 ? '' : 'none';
            dlAllBtn.onclick = () => {
                window.location.href = 'jar2wsdl/download-all?jobId=' + currentJobId;
            };

            entries.forEach(e => {
                const shortName = e.name.includes('/')
                    ? e.name.substring(e.name.lastIndexOf('/') + 1)
                    : e.name;
                const item = document.createElement('div');
                item.className = 'jar2wsdl-wsdl-item';
                item.innerHTML = `
                    <div class="jar2wsdl-wsdl-item-info">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                            <polyline points="14 2 14 8 20 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                        <div>
                            <span class="jar2wsdl-wsdl-item-name">${shortName}</span>
                            <span class="jar2wsdl-wsdl-item-path">${e.name}</span>
                        </div>
                    </div>
                    <button class="jar2wsdl-dl-btn" onclick="downloadSingle('${currentJobId}','${encodeURIComponent(e.name)}')">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none">
                            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                            <polyline points="7 10 12 15 17 10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                            <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                        </svg>
                        İndir
                    </button>`;
                wsdlList.appendChild(item);
            });
        }

        show(resultBox);
    }

    function reset() {
        hide(resultBox); hide(errorBox);
        show(dropArea);
        fileInput.value = '';
        currentJobId = null;
    }

    function showError(msg) {
        errorBox.textContent = '⚠ ' + msg;
        show(errorBox);
    }

    function show(el) { el.style.display = ''; }
    function hide(el) { el.style.display = 'none'; }

    window.downloadSingle = function (jobId, name) {
        window.location.href = 'jar2wsdl/download?jobId=' + jobId + '&name=' + name;
    };
})();
