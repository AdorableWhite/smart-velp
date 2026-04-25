import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskList } from '../components/library/TaskList';
import { clearFailedTasks, deleteTask, fetchTaskStatus, fetchTasks, submitLocalSubtitleAnalyze } from '../services/api/media';
import { getImportedMedia, listImportedMedia, updateImportedMediaSubtitleTask } from '../services/storage/localMedia';
import { getTranslationProfile } from '../services/storage/translationProfiles';
import { usePreferencesStore } from '../store/usePreferencesStore';
import type { ImportedMediaSummary } from '../types/media';

const localStatusLabel: Record<string, string> = {
  pending: '字幕排队中',
  processing: '字幕重译中',
  completed: '字幕已更新',
  failed: '字幕重译失败'
};

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

  const retranslateMutation = useMutation({
    mutationFn: async (itemId: string) => {
      const item = await getImportedMedia(itemId);
      if (!item?.subtitleFile) {
        throw new Error('该本地内容没有字幕文件');
      }

      const selectedProfile = await getTranslationProfile(selectedProfileId);
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
              apiKey: selectedProfile.apiKey
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

        <TaskList
          tasks={tasksQuery.data ?? []}
          onOpen={(taskId) => navigate(`/study/backend/${taskId}`)}
          onDelete={(taskId) => deleteMutation.mutate(taskId)}
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
