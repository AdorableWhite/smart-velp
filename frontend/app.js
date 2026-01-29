// API Base Configuration
const API_BASE = '/api';

// State Management
let state = {
    tasks: [],
    currentTaskId: null,
    subtitles: [],
    currentSubIndex: -1,
    subMode: 'dual',
    pollingInterval: null,
    playbackRate: 1.0,
    isLooping: false,
    fontSize: 24
};

// DOM Elements
const elements = {
    taskList: document.getElementById('taskList'),
    youtubeUrl: document.getElementById('youtubeUrl'),
    submitBtn: document.getElementById('submitBtn'),
    welcomeView: document.getElementById('welcomeView'),
    statusView: document.getElementById('statusView'),
    playerView: document.getElementById('playerView'),
    statusText: document.getElementById('statusText'),
    progressFill: document.getElementById('progressFill'),
    mainVideo: document.getElementById('mainVideo'),
    subtitleList: document.getElementById('subtitleList'),
    subModeSelect: document.getElementById('subModeSelect'),
    speedSlider: document.getElementById('speedSlider'),
    speedValue: document.getElementById('speedValue'),
    loopBtn: document.getElementById('loopBtn'),
    fullScreenBtn: document.getElementById('fullScreenBtn'),
    fontSizeSlider: document.getElementById('fontSizeSlider'),
    fontSizeValue: document.getElementById('fontSizeValue'),
    speedBtns: document.querySelectorAll('.speed-btn'),
    videoContainer: document.getElementById('videoContainer'),
    videoSubtitleOverlay: document.getElementById('videoSubtitleOverlay'),
    overlayEn: document.querySelector('.overlay-en'),
    overlayCn: document.querySelector('.overlay-cn')
};

/**
 * Initialize the application
 */
function init() {
    // Event Listeners
    elements.submitBtn.addEventListener('click', startParser);
    elements.subModeSelect.addEventListener('change', (e) => {
        state.subMode = e.target.value;
        renderSubtitles();
        // Update overlay immediately if visible
        if (state.currentSubIndex !== -1) {
            const sub = state.subtitles[state.currentSubIndex];
            elements.overlayEn.innerText = state.subMode !== 'cn' ? sub.en : '';
            elements.overlayCn.innerText = state.subMode !== 'en' ? sub.cn : '';
            if (state.subMode === 'none') {
                elements.overlayEn.innerText = '';
                elements.overlayCn.innerText = '';
            }
        }
    });

    elements.mainVideo.addEventListener('timeupdate', () => {
        syncSubtitles(elements.mainVideo.currentTime);
    });

    // Speed Control Listeners
    elements.speedBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const speed = parseFloat(btn.dataset.speed);
            setPlaybackRate(speed);
        });
    });

    elements.speedSlider.addEventListener('input', (e) => {
        const speed = parseFloat(e.target.value);
        setPlaybackRate(speed, false); // false means don't update slider value to avoid feedback loop
    });

    // Loop Control Listener
    elements.loopBtn.addEventListener('click', toggleLoop);

    // Fullscreen Control Listener
    elements.fullScreenBtn.addEventListener('click', toggleFullScreen);

    // Font Size Control Listener
    elements.fontSizeSlider.addEventListener('input', (e) => {
        const size = e.target.value;
        setFontSize(size);
    });

    // Fullscreen Change Listener
    const handleFullscreenChange = () => {
        const isFullscreen = document.fullscreenElement !== null;
        if (isFullscreen) {
            elements.videoSubtitleOverlay.classList.remove('hidden');
        } else {
            elements.videoSubtitleOverlay.classList.add('hidden');
        }
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange); // Safari support

    // Set initial font size
    setFontSize(state.fontSize);

    // Initial Load
    loadTasks();
    
    // Auto-refresh task list every 10 seconds
    setInterval(loadTasks, 10000);
}

/**
 * Set video playback rate
 */
function setPlaybackRate(speed, updateSlider = true) {
    state.playbackRate = speed;
    elements.mainVideo.playbackRate = speed;
    elements.speedValue.innerText = speed.toFixed(1) + 'x';
    
    if (updateSlider) {
        elements.speedSlider.value = speed;
    }

    // Update active class on buttons
    elements.speedBtns.forEach(btn => {
        if (parseFloat(btn.dataset.speed) === speed) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
}

/**
 * Toggle sentence loop mode
 */
function toggleLoop() {
    state.isLooping = !state.isLooping;
    elements.loopBtn.classList.toggle('active', state.isLooping);
}

/**
 * Toggle custom fullscreen
 */
function toggleFullScreen() {
    if (!document.fullscreenElement) {
        elements.videoContainer.requestFullscreen().catch(err => {
            console.error(`无法进入全屏: ${err.message}`);
        });
    } else {
        document.exitFullscreen();
    }
}

/**
 * Set subtitle font size
 */
function setFontSize(size) {
    state.fontSize = size;
    elements.fontSizeValue.innerText = size + 'px';
    elements.videoContainer.style.setProperty('--sub-font-size', size + 'px');
}

/**
 * Load all tasks from backend
 */
async function loadTasks() {
    try {
        // Note: Assuming there's an endpoint to get all tasks. 
        // If not, we might need to implement one or use local storage for task history.
        // For now, let's assume /api/parser/tasks exists or we'll handle the error.
        const res = await fetch(`${API_BASE}/parser/tasks`);
        if (!res.ok) throw new Error('Failed to fetch tasks');
        
        const tasks = await res.json();
        state.tasks = tasks;
        renderTaskList();
    } catch (e) {
        console.warn('Task list loading failed (endpoint might not exist yet):', e);
        // Fallback: If no endpoint, we can't show the list unless we track it locally
    }
}

/**
 * Render the sidebar task list
 */
function renderTaskList() {
    if (!elements.taskList) return;

    if (state.tasks.length === 0) {
        elements.taskList.innerHTML = '<div class="no-tasks">暂无任务</div>';
        return;
    }

    elements.taskList.innerHTML = state.tasks.map(task => {
        const isCompleted = task.status === 'completed';
        const isActive = state.currentTaskId === task.taskId;
        const displayTitle = task.title || task.url;
        
        return `
            <div class="task-item status-${task.status} ${isActive ? 'active' : ''}" 
                 data-task-id="${task.taskId}"
                 title="链接: ${task.url}\n状态: ${getStatusLabel(task.status)}\n进度: ${task.progress || 0}%"
                 style="cursor: ${isCompleted ? 'pointer' : 'not-allowed'}">
                <div class="task-title" title="${displayTitle}">${displayTitle}</div>
                <div class="task-status">
                    <span class="status-dot dot-${task.status}"></span>
                    ${getStatusLabel(task.status)} ${task.progress ? `(${task.progress}%)` : ''}
                </div>
            </div>
        `;
    }).join('');

    // Add click listeners to task items
    elements.taskList.querySelectorAll('.task-item').forEach(item => {
        item.addEventListener('click', () => {
            const taskId = item.dataset.taskId;
            const task = state.tasks.find(t => t.taskId === taskId);
            if (task && task.status === 'completed') {
                selectTask(taskId);
            }
        });
    });
}

function getStatusLabel(status) {
    const labels = {
        'completed': '已完成',
        'processing': '处理中',
        'failed': '失败',
        'pending': '等待中'
    };
    return labels[status] || status;
}

/**
 * Handle task selection
 */
async function selectTask(taskId) {
    state.currentTaskId = taskId;
    renderTaskList();
    switchView('player');
    
    try {
        // First get task info to get videoId
        const resTask = await fetch(`${API_BASE}/parser/status/${taskId}`);
        const taskData = await resTask.json();
        
        if (taskData.videoId) {
            loadCourse(taskData.videoId);
        }
    } catch (e) {
        console.error('Failed to load task details', e);
    }
}

/**
 * Submit new video URL
 */
async function startParser() {
    const url = elements.youtubeUrl.value.trim();
    if (!url) {
        alert('请输入有效的 YouTube 链接');
        return;
    }

    // Reset button state immediately to allow multiple submissions
    elements.submitBtn.disabled = true;
    const originalBtnText = elements.submitBtn.innerText;
    elements.submitBtn.innerText = '提交中...';

    try {
        const res = await fetch(`${API_BASE}/parser/analyze`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ url })
        });
        const data = await res.json();
        
        if (data.taskId) {
            // Switch to status view for the NEW task
            switchView('status');
            startPolling(data.taskId);
            loadTasks(); // Refresh list to show new task
        } else {
            throw new Error('任务提交失败');
        }
    } catch (e) {
        alert('提交失败: ' + e.message);
    } finally {
        // Re-enable button so user can submit another link immediately
        elements.submitBtn.disabled = false;
        elements.submitBtn.innerText = originalBtnText;
    }
}

/**
 * Poll task status
 */
function startPolling(taskId) {
    if (state.pollingInterval) clearInterval(state.pollingInterval);

    state.pollingInterval = setInterval(async () => {
        try {
            const res = await fetch(`${API_BASE}/parser/status/${taskId}`);
            const data = await res.json();

            elements.progressFill.style.width = data.progress + '%';
            elements.statusText.innerText = `${getStatusLabel(data.status)}... (${data.progress}%)`;

            if (data.status === 'completed') {
                clearInterval(state.pollingInterval);
                loadTasks();
                selectTask(taskId);
            } else if (data.status === 'failed') {
                clearInterval(state.pollingInterval);
                elements.statusText.innerText = '处理失败: ' + (data.error || '未知错误');
                elements.submitBtn.disabled = false;
                loadTasks();
            }
        } catch (e) {
            console.error('Polling error', e);
        }
    }, 2000);
}

/**
 * Load course data (video + subtitles)
 */
async function loadCourse(videoId) {
    try {
        const res = await fetch(`${API_BASE}/course/${videoId}/detail`);
        const data = await res.json();

        elements.mainVideo.src = data.videoUrl; 
        elements.mainVideo.playbackRate = state.playbackRate; // Re-apply current speed
        state.subtitles = data.subtitles || [];
        state.currentSubIndex = -1;
        renderSubtitles();
    } catch (e) {
        console.error(e);
        alert('加载内容失败');
    }
}

/**
 * Render subtitles in the right panel
 */
function renderSubtitles() {
    elements.subtitleList.className = `subtitle-list mode-${state.subMode}`;
    
    elements.subtitleList.innerHTML = state.subtitles.map((sub, index) => `
        <div class="sub-item" id="sub-${index}" data-start="${sub.startTime}">
            <div class="en-text">${sub.en || ''}</div>
            <div class="cn-text">${sub.cn || ''}</div>
        </div>
    `).join('');
    
    // Add click listeners
    document.querySelectorAll('.sub-item').forEach(item => {
        item.addEventListener('click', () => {
            elements.mainVideo.currentTime = parseFloat(item.dataset.start);
            elements.mainVideo.play();
        });
    });
}

/**
 * Sync subtitle highlighting with video time
 */
function syncSubtitles(time) {
    // Handle Looping: if looping is enabled and we passed the current subtitle's end time
    if (state.isLooping && state.currentSubIndex !== -1) {
        const currentSub = state.subtitles[state.currentSubIndex];
        // Add a small buffer (0.1s) to ensure we don't skip the loop due to timeupdate granularity
        if (time >= currentSub.endTime - 0.1) {
            elements.mainVideo.currentTime = currentSub.startTime;
            return;
        }
    }

    const index = state.subtitles.findIndex(s => time >= s.startTime && time < s.endTime);
    
    if (index !== -1 && index !== state.currentSubIndex) {
        const oldSub = document.getElementById(`sub-${state.currentSubIndex}`);
        if (oldSub) oldSub.classList.remove('active');
        
        const currentSub = document.getElementById(`sub-${index}`);
        if (currentSub) {
            currentSub.classList.add('active');
            currentSub.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
        
        state.currentSubIndex = index;
        
        // Update overlay text if in fullscreen
        const currentSubData = state.subtitles[index];
        if (currentSubData) {
            elements.overlayEn.innerText = state.subMode !== 'cn' ? currentSubData.en : '';
            elements.overlayCn.innerText = state.subMode !== 'en' ? currentSubData.cn : '';
            
            // Handle 'none' mode
            if (state.subMode === 'none') {
                elements.overlayEn.innerText = '';
                elements.overlayCn.innerText = '';
            }
        }
    } else if (index === -1 && state.currentSubIndex !== -1) {
        // Clear overlay if no subtitle matches current time
        elements.overlayEn.innerText = '';
        elements.overlayCn.innerText = '';
        state.currentSubIndex = -1;
    }
}

/**
 * Helper to switch between different views
 */
function switchView(viewName) {
    elements.welcomeView.classList.add('hidden');
    elements.statusView.classList.add('hidden');
    elements.playerView.classList.add('hidden');

    if (viewName === 'welcome') elements.welcomeView.classList.remove('hidden');
    if (viewName === 'status') elements.statusView.classList.remove('hidden');
    if (viewName === 'player') elements.playerView.classList.remove('hidden');
}

// Initialize on load
document.addEventListener('DOMContentLoaded', init);
