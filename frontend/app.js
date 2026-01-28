// Config - using relative path relying on Vite proxy in dev
const API_BASE = '/api';

function init() {
    const submitBtn = document.getElementById('submitBtn');
    if (submitBtn) {
        submitBtn.addEventListener('click', startParser);
    }
    
    // Check if we are on player page
    if (window.location.pathname.includes('player.html')) {
        const urlParams = new URLSearchParams(window.location.search);
        const videoId = urlParams.get('id');
        if (videoId) {
            loadCourse(videoId);
        } else {
            alert('未找到视频 ID');
        }
    }
}

async function startParser() {
    const urlInput = document.getElementById('youtubeUrl');
    const url = urlInput.value.trim();
    
    if (!url) {
        alert('请输入有效的 YouTube 链接');
        return;
    }

    const submitBtn = document.getElementById('submitBtn');
    const statusSection = document.getElementById('statusSection');
    const statusText = document.getElementById('statusText');

    submitBtn.disabled = true;
    statusSection.classList.remove('hidden');

    try {
        const res = await fetch(`${API_BASE}/parser/analyze`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        });
        const data = await res.json();
        
        if (data.taskId) {
            pollStatus(data.taskId);
        } else {
            throw new Error('Task submission failed');
        }

    } catch (e) {
        console.error(e);
        statusText.innerText = '提交失败: ' + e.message;
        submitBtn.disabled = false;
    }
}

async function pollStatus(taskId) {
    const statusText = document.getElementById('statusText');
    const progressFill = document.getElementById('progressFill');

    const interval = setInterval(async () => {
        try {
            const res = await fetch(`${API_BASE}/parser/status/${taskId}`);
            const data = await res.json();

            if (progressFill) progressFill.style.width = data.progress + '%';
            if (statusText) statusText.innerText = `${data.error || '处理中...'} (${data.progress}%)`;

            if (data.status === 'completed') {
                clearInterval(interval);
                statusText.innerText = '完成！即将跳转...';
                setTimeout(() => {
                    window.location.href = `player.html?id=${data.videoId}`;
                }, 1000);
            } else if (data.status === 'failed') {
                clearInterval(interval);
                statusText.innerText = '处理失败: ' + (data.error || 'Unknown error');
                document.getElementById('submitBtn').disabled = false;
            }

        } catch (e) {
            console.error('Polling error', e);
        }
    }, 2000);
}

// Player Logic
let subtitles = [];
let currentSubIndex = -1;
let subMode = 'dual'; // dual, en, cn, none

async function loadCourse(videoId) {
    try {
        const res = await fetch(`${API_BASE}/course/${videoId}/detail`);
        const data = await res.json();

        // Setup Video
        const video = document.getElementById('mainVideo');
        video.src = data.videoUrl; 
        
        // Setup Subtitles
        subtitles = data.subtitles || [];
        renderSubtitles();

        // Sync Logic
        video.addEventListener('timeupdate', () => {
            syncSubtitles(video.currentTime);
        });
        
        // Attach mode change listener
        const modeSelect = document.getElementById('subModeSelect');
        if (modeSelect) {
            modeSelect.addEventListener('change', (e) => {
                subMode = e.target.value;
                updateSubtitleVisibility();
            });
        }

    } catch (e) {
        console.error(e);
        alert('加载课程失败');
    }
}

function renderSubtitles() {
    const list = document.getElementById('subtitleList');
    if (!list) return;
    
    list.innerHTML = subtitles.map((sub, index) => `
        <div class="sub-item" id="sub-${index}" data-start="${sub.startTime}">
            <div class="en-text">${sub.en || ''}</div>
            <div class="cn-text">${sub.cn || ''}</div>
        </div>
    `).join('');
    
    // Add click listeners
    document.querySelectorAll('.sub-item').forEach(item => {
        item.addEventListener('click', () => {
            const time = parseFloat(item.dataset.start);
            seekVideo(time);
        });
    });
    
    updateSubtitleVisibility();
}

function updateSubtitleVisibility() {
    const list = document.getElementById('subtitleList');
    if (!list) return;

    list.className = `subtitle-list mode-${subMode}`;
}

function syncSubtitles(time) {
    const index = subtitles.findIndex(s => time >= s.startTime && time < s.endTime);
    
    if (index !== -1 && index !== currentSubIndex) {
        if (currentSubIndex !== -1) {
            const old = document.getElementById(`sub-${currentSubIndex}`);
            if (old) old.classList.remove('active');
        }
        
        const current = document.getElementById(`sub-${index}`);
        if (current) {
            current.classList.add('active');
            current.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        
        currentSubIndex = index;
    }
}

function seekVideo(time) {
    const video = document.getElementById('mainVideo');
    if (video) {
        video.currentTime = time;
        video.play();
    }
}


// Initialize
init();
