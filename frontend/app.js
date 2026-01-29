// API Base Configuration
// 支持环境变量配置，用于 GitHub Pages 部署
// 如果设置了 VITE_API_BASE，使用该值；否则根据当前域名判断
// 开发环境或同域名部署使用相对路径，GitHub Pages 使用完整后端域名
const getApiBase = () => {
    // 优先使用环境变量（构建时注入）
    if (import.meta.env.VITE_API_BASE) {
        return import.meta.env.VITE_API_BASE;
    }
    
    // 如果是 localhost 或同域名，使用相对路径
    const hostname = window.location.hostname;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
        return '/api';
    }
    
    // 生产环境：如果前端在 GitHub Pages，后端在自定义域名
    // 使用当前协议和主域名（去掉 www 前缀）作为后端地址
    const protocol = window.location.protocol;
    const domain = hostname.replace(/^www\./, ''); // 去掉 www 前缀
    return `${protocol}//${domain}/api`;
};

const API_BASE = getApiBase();

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
    clearFailedBtn: document.getElementById('clearFailedBtn'),
    speedBtns: document.querySelectorAll('.speed-btn'),
    videoContainer: document.getElementById('videoContainer'),
    videoSubtitleOverlay: document.getElementById('videoSubtitleOverlay'),
    overlayEn: document.querySelector('.overlay-en'),
    overlayCn: document.querySelector('.overlay-cn'),
    // Modal Elements
    customModal: document.getElementById('customModal'),
    modalTitle: document.getElementById('modalTitle'),
    modalMessage: document.getElementById('modalMessage'),
    modalConfirmBtn: document.getElementById('modalConfirmBtn'),
    modalCancelBtn: document.getElementById('modalCancelBtn')
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

    elements.clearFailedBtn.addEventListener('click', clearFailedTasks);

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
        const res = await fetch(`${API_BASE}/parser/tasks`);
        if (!res.ok) throw new Error('Failed to fetch tasks');
        
        let tasks = await res.json();
        // Sort by createdAt descending (newest first)
        tasks.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
        
        state.tasks = tasks;
        renderTaskList();
    } catch (e) {
        console.warn('Task list loading failed:', e);
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
                 style="cursor: ${isCompleted ? 'pointer' : 'default'}">
                <button class="delete-task" data-task-id="${task.taskId}" title="删除任务">✕</button>
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
        item.addEventListener('click', (e) => {
            // Don't trigger task selection if delete button was clicked
            if (e.target.classList.contains('delete-task')) return;
            
            const taskId = item.dataset.taskId;
            const task = state.tasks.find(t => t.taskId === taskId);
            if (task && task.status === 'completed') {
                selectTask(taskId);
            }
        });
    });

    // Add click listeners to delete buttons
    elements.taskList.querySelectorAll('.delete-task').forEach(btn => {
        btn.addEventListener('click', async (e) => {
            e.stopPropagation();
            const taskId = btn.dataset.taskId;
            const confirmed = await showModal('确认删除', '确定要彻底删除这个学习任务吗？删除后将无法找回。');
            if (confirmed) {
                deleteTask(taskId);
            }
        });
    });
}

/**
 * Custom Modal implementation
 * @returns {Promise<boolean>}
 */
function showModal(title, message, options = { showCancel: true, confirmText: '确定', cancelText: '取消' }) {
    return new Promise((resolve) => {
        elements.modalTitle.innerText = title;
        elements.modalMessage.innerText = message;
        elements.modalConfirmBtn.innerText = options.confirmText || '确定';
        elements.modalCancelBtn.innerText = options.cancelText || '取消';
        
        if (options.showCancel === false) {
            elements.modalCancelBtn.classList.add('hidden');
        } else {
            elements.modalCancelBtn.classList.remove('hidden');
        }

        const handleConfirm = () => {
            cleanup();
            resolve(true);
        };

        const handleCancel = () => {
            cleanup();
            resolve(false);
        };

        const cleanup = () => {
            elements.modalConfirmBtn.removeEventListener('click', handleConfirm);
            elements.modalCancelBtn.removeEventListener('click', handleCancel);
            elements.customModal.classList.add('hidden');
        };

        elements.modalConfirmBtn.addEventListener('click', handleConfirm);
        elements.modalCancelBtn.addEventListener('click', handleCancel);
        elements.customModal.classList.remove('hidden');
    });
}

/**
 * Delete a single task
 */
async function deleteTask(taskId) {
    try {
        const res = await fetch(`${API_BASE}/parser/tasks/${taskId}`, {
            method: 'DELETE'
        });
        if (res.ok) {
            if (state.currentTaskId === taskId) {
                state.currentTaskId = null;
                switchView('welcome');
            }
            loadTasks();
        } else {
            showModal('删除失败', '服务器返回错误，请稍后再试。', { showCancel: false });
        }
    } catch (e) {
        console.error('Delete task error', e);
        showModal('删除失败', '无法连接到服务器: ' + e.message, { showCancel: false });
    }
}

/**
 * Clear all failed tasks
 */
async function clearFailedTasks() {
    const confirmed = await showModal('清理异常任务', '系统将清理所有状态为“失败”的任务。确定要继续吗？');
    if (!confirmed) return;
    
    try {
        const res = await fetch(`${API_BASE}/parser/tasks/failed`, {
            method: 'DELETE'
        });
        if (res.ok) {
            loadTasks();
        } else {
            showModal('清理失败', '清理过程中出现服务器错误。', { showCancel: false });
        }
    } catch (e) {
        console.error('Clear failed tasks error', e);
        showModal('清理失败', '无法连接到服务器: ' + e.message, { showCancel: false });
    }
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
        showModal('提示', '请输入有效的 YouTube 视频链接。', { showCancel: false });
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
        showModal('提交失败', '提交过程中出现错误: ' + e.message, { showCancel: false });
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
        showModal('加载失败', '无法加载视频内容，请检查网络或稍后重试。', { showCancel: false });
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
