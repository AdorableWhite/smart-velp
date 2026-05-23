import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskList } from '../components/library/TaskList';
import {
  clearFailedTasks,
  deleteTask,
  fetchDownloadDiagnostics,
  fetchTaskStatus,
  fetchTasks,
  submitAnalyze,
  submitLocalSubtitleAnalyze
} from '../services/api/media';
import { getImportedMedia, listImportedMedia, updateImportedMediaSubtitleTask } from '../services/storage/localMedia';
import { getPreferredTranslationProfile } from '../services/storage/translationProfiles';
import { usePreferencesStore } from '../store/usePreferencesStore';
import type { DownloadDiagnostics, ImportedMediaSummary, TaskSummary } from '../types/media';

const localStatusLabel: Record<string, string> = {
  pending: '字幕排队中',
  processing: '字幕重译中',
  completed: '字幕已更新',
  failed: '字幕重译失败'
};

function buildDownloadReadiness(diagnostics?: DownloadDiagnostics) {
  if (!diagnostics) {
    return undefined;
  }

  const issues: string[] = [];
  if (!diagnostics.ytDlpPath) {
    issues.push('未检测到 yt-dlp，无法下载 YouTube 视频。');
  }
  if (!diagnostics.ffmpegAvailable) {
    issues.push('未检测到 ffmpeg，高质量音视频合并和部分格式会更容易失败。');
  }
  if (!diagnostics.cookiesConfigured) {
    issues.push('未配置 Cookies，登录验证、年龄限制和反机器人校验类视频可能失败。');
  }
  if (diagnostics.formatFallbacks.length < 2) {
    issues.push('格式回退较少，遇到特殊视频时容错能力偏弱。');
  }

  if (!issues.length) {
    return {
      level: 'ready',
      title: 'YouTube 下载环境稳定',
      description: 'yt-dlp、ffmpeg、Cookies 和格式回退都已就绪。',
      issues
    };
  }

  return {
    level: diagnostics.ytDlpPath ? 'warning' : 'danger',
    title: diagnostics.ytDlpPath ? 'YouTube 下载环境有风险' : 'YouTube 下载环境不可用',
    description: issues[0],
    issues
  };
}

export function LibraryPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [localItems, setLocalItems] = useState<ImportedMediaSummary[]>([]);
  const sourceLang = usePreferencesStore((state) => state.sourceLang);
  const targetLang = usePreferencesStore((state) => state.targetLang);
  const selectedProfileId = usePreferencesStore((state) => state.selectedProfileId);

  const tasksQuery = useQuery({
    queryKey: ['tasks'],
    queryFn: fetchTasks,
    refetchInterval: 10_000
  });

  const downloadDiagnosticsQuery = useQuery({
    queryKey: ['download-diagnostics'],
    queryFn: fetchDownloadDiagnostics,
    refetchInterval: 60_000
  });

  const downloadReadiness = useMemo(
    () => buildDownloadReadiness(downloadDiagnosticsQuery.data),
    [downloadDiagnosticsQuery.data]
  );

  const taskHealth = useMemo(() => {
    const tasks = tasksQuery.data ?? [];
    return {
      ready: tasks.filter((task) => task.status === 'completed' && task.assetAvailable !== false).length,
      running: tasks.filter((task) => task.status === 'pending' || task.status === 'processing').length,
      failed: tasks.filter((task) => task.status === 'failed').length,
      assetMissing: tasks.filter((task) => task.status === 'completed' && task.assetAvailable === false).length
    };
  }, [tasksQuery.data]);

  useEffect(() => {
    void listImportedMedia().then(setLocalItems);
  }, []);

  const reloadLocalItems = async () => {
    setLocalItems(await listImportedMedia());
  };

  const localTaskItems = useMemo(
    () => localItems.filter((item) => item.subtitleTaskId),
    [localItems]
  );

  const localStatusQueries = useQueries({
    queries: localTaskItems.map((item) => ({
      queryKey: ['local-task-status', item.subtitleTaskId],
      queryFn: () => fetchTaskStatus(item.subtitleTaskId ?? ''),
      refetchInterval: (query: { state: { data?: { status?: string } } }) => {
        const status = query.state.data?.status;
        return status === 'completed' || status === 'failed' ? false : 2_000;
      }
    }))
  });

  const localStatusMap = useMemo(
    () =>
      new Map(
        localTaskItems.map((item, index) => [
          item.subtitleTaskId,
          localStatusQueries[index]?.data
        ])
      ),
    [localStatusQueries, localTaskItems]
  );

  const deleteMutation = useMutation({
    mutationFn: deleteTask,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] })
  });

  const clearFailedMutation = useMutation({
    mutationFn: clearFailedTasks,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] })
  });

  const retryMutation = useMutation({
    mutationFn: async (task: TaskSummary) => {
      const selectedProfile = await getPreferredTranslationProfile(selectedProfileId);
      return submitAnalyze({
        url: task.url,
        sourceLang: task.sourceLang ?? sourceLang,
        targetLang: task.targetLang ?? targetLang,
        translationProfile: selectedProfile
          ? {
              provider: selectedProfile.provider,
              baseUrl: selectedProfile.baseUrl,
              model: selectedProfile.model,
              apiKey: selectedProfile.apiKey,
              prompt: selectedProfile.prompt
            }
          : undefined
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tasks'] })
  });

  const retranslateMutation = useMutation({
    mutationFn: async (itemId: string) => {
      const item = await getImportedMedia(itemId);
      if (!item?.subtitleFile) {
        throw new Error('该本地内容没有字幕文件。');
      }

      const selectedProfile = await getPreferredTranslationProfile(selectedProfileId);
      const subtitleContent = await item.subtitleFile.text();
      const result = await submitLocalSubtitleAnalyze({
        title: item.title,
        subtitleContent,
        subtitleFormat: item.subtitleName?.toLowerCase().endsWith('.srt') ? 'srt' : 'vtt',
        sourceLang,
        targetLang,
        translationProfile: selectedProfile
          ? {
              provider: selectedProfile.provider,
              baseUrl: selectedProfile.baseUrl,
              model: selectedProfile.model,
              apiKey: selectedProfile.apiKey,
              prompt: selectedProfile.prompt
            }
          : undefined
      });

      await updateImportedMediaSubtitleTask(itemId, result.taskId);
      await reloadLocalItems();
      return result.taskId;
    }
  });

  return (
    <div className="page-stack">
      <section className="card">
        <div className="section-head">
          <div>
            <p className="eyebrow">在线内容</p>
            <h3>后端任务队列</h3>
          </div>

          <button type="button" className="secondary-button" onClick={() => clearFailedMutation.mutate()}>
            清理失败任务
          </button>
        </div>

        {downloadReadiness ? (
          <div className={`diagnostics-panel diagnostics-panel-${downloadReadiness.level}`}>
            <div>
              <strong>{downloadReadiness.title}</strong>
              <span>{downloadReadiness.description}</span>
            </div>
            {downloadReadiness.issues.length > 1 ? (
              <span className="diagnostics-count">{downloadReadiness.issues.length} 项需要关注</span>
            ) : null}
          </div>
        ) : null}

        {downloadDiagnosticsQuery.data ? (
          <div className="diagnostics-row">
            <span>yt-dlp {downloadDiagnosticsQuery.data.ytDlpVersion ?? 'unknown'}</span>
            <span title={downloadDiagnosticsQuery.data.ffmpegPath ?? undefined}>
              {downloadDiagnosticsQuery.data.ffmpegAvailable ? 'ffmpeg 可用，支持高质量合并' : 'ffmpeg 未安装，使用无合并回退'}
            </span>
            <span>
              {downloadDiagnosticsQuery.data.cookiesConfigured
                ? `Cookies ${downloadDiagnosticsQuery.data.cookieSource}`
                : '未启用 Cookies'}
            </span>
            <span>{downloadDiagnosticsQuery.data.formatFallbacks.length} 组格式回退</span>
            <span>{downloadDiagnosticsQuery.data.timeoutMinutes} 分钟超时</span>
          </div>
        ) : null}

        <div className="task-health-row" aria-label="任务健康概览">
          <span>
            <strong>{taskHealth.ready}</strong>
            可学习
          </span>
          <span>
            <strong>{taskHealth.running}</strong>
            处理中
          </span>
          <span>
            <strong>{taskHealth.failed}</strong>
            失败
          </span>
          <span>
            <strong>{taskHealth.assetMissing}</strong>
            资源缺失
          </span>
        </div>

        <TaskList
          tasks={tasksQuery.data ?? []}
          onOpen={(taskId) => navigate(`/study/backend/${taskId}`)}
          onDelete={(taskId) => deleteMutation.mutate(taskId)}
          onRetry={(task) => retryMutation.mutate(task)}
        />
      </section>

      <section className="card">
        <div className="section-head">
          <div>
            <p className="eyebrow">本地内容</p>
            <h3>导入后即可离线打开</h3>
          </div>
        </div>

        {localItems.length ? (
          <div className="task-list-grid">
            {localItems.map((item) => {
              const taskStatus = item.subtitleTaskId ? localStatusMap.get(item.subtitleTaskId) : undefined;
              return (
                <article key={item.id} className="task-card status-completed">
                  <div className="task-card-head">
                    <span className="status-badge">
                      {item.subtitleTaskId
                        ? localStatusLabel[taskStatus?.status ?? 'processing']
                        : '仅本地'}
                    </span>
                  </div>
                  <h3>{item.title}</h3>
                  <p className="task-url">
                    {item.videoName}
                    {item.subtitleName ? ` + ${item.subtitleName}` : ''}
                  </p>
                  <div className="task-card-foot">
                    <span>{new Date(item.createdAt).toLocaleString()}</span>
                    <div className="inline-actions">
                      {item.subtitleName ? (
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => retranslateMutation.mutate(item.id)}
                        >
                          重新翻译
                        </button>
                      ) : null}
                      <button type="button" className="text-button" onClick={() => navigate(`/study/local/${item.id}`)}>
                        打开
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <div className="empty-card">还没有本地导入内容。</div>
        )}
      </section>
    </div>
  );
}
