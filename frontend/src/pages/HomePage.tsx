import { useMutation, useQuery } from '@tanstack/react-query';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskList } from '../components/library/TaskList';
import { fetchTasks, submitAnalyze, submitLocalSubtitleAnalyze } from '../services/api/media';
import { listImportedMedia, saveImportedMedia } from '../services/storage/localMedia';
import { getPreferredTranslationProfile } from '../services/storage/translationProfiles';
import { usePreferencesStore } from '../store/usePreferencesStore';
import type { ImportedMediaSummary } from '../types/media';

export function HomePage() {
  const navigate = useNavigate();
  const [url, setUrl] = useState('');
  const [videoFile, setVideoFile] = useState<File>();
  const [subtitleFile, setSubtitleFile] = useState<File>();
  const [localItems, setLocalItems] = useState<ImportedMediaSummary[]>([]);
  const sourceLang = usePreferencesStore((state) => state.sourceLang);
  const targetLang = usePreferencesStore((state) => state.targetLang);
  const selectedProfileId = usePreferencesStore((state) => state.selectedProfileId);

  const tasksQuery = useQuery({
    queryKey: ['tasks'],
    queryFn: fetchTasks,
    refetchInterval: 10_000
  });

  const submitMutation = useMutation({
    mutationFn: submitAnalyze,
    onSuccess: (result) => {
      navigate(`/study/backend/${result.taskId}`);
      setUrl('');
    }
  });

  useEffect(() => {
    void listImportedMedia().then(setLocalItems);
  }, []);

  const stats = useMemo(() => {
    const tasks = tasksQuery.data ?? [];
    return {
      total: tasks.length + localItems.length,
      completed: tasks.filter((task) => task.status === 'completed').length + localItems.length,
      processing: tasks.filter((task) => task.status === 'processing' || task.status === 'pending').length
    };
  }, [localItems.length, tasksQuery.data]);

  const continueItems = useMemo(() => {
    const onlineItems = (tasksQuery.data ?? [])
      .filter((task) => task.status === 'completed')
      .slice(0, 2)
      .map((task) => ({
        id: task.taskId,
        title: task.title || task.url,
        meta: '在线课程',
        path: `/study/backend/${task.taskId}`
      }));

    const localCards = localItems.slice(0, 2).map((item) => ({
      id: item.id,
      title: item.title,
      meta: '本地内容',
      path: `/study/local/${item.id}`
    }));

    return [...onlineItems, ...localCards].slice(0, 4);
  }, [localItems, tasksQuery.data]);

  const onSubmitUrl = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!url.trim()) {
      return;
    }

    const selectedProfile = await getPreferredTranslationProfile(selectedProfileId);
    await submitMutation.mutateAsync({
      url: url.trim(),
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
  };

  const onImport = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const selectedProfile = await getPreferredTranslationProfile(selectedProfileId);
    let subtitleTaskId: string | undefined;

    if (!videoFile && subtitleFile) {
      const subtitleContent = await subtitleFile.text();
      const result = await submitLocalSubtitleAnalyze({
        title: subtitleFile.name.replace(/\.[^/.]+$/, ''),
        subtitleContent,
        subtitleFormat: subtitleFile.name.toLowerCase().endsWith('.srt') ? 'srt' : 'vtt',
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
      navigate(`/study/backend/${result.taskId}`);
      return;
    }

    if (!videoFile) {
      return;
    }

    if (subtitleFile) {
      const subtitleContent = await subtitleFile.text();
      const result = await submitLocalSubtitleAnalyze({
        title: videoFile.name.replace(/\.[^/.]+$/, ''),
        subtitleContent,
        subtitleFormat: subtitleFile.name.toLowerCase().endsWith('.srt') ? 'srt' : 'vtt',
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
      subtitleTaskId = result.taskId;
    }

    const mediaId = await saveImportedMedia(videoFile, subtitleFile, subtitleTaskId);
    navigate(`/study/local/${mediaId}`);
  };

  return (
    <div className="page-stack">
      <section className="hero hero--compact card">
        <div className="hero-grid">
          <div className="hero-copy-block">
            <p className="eyebrow">Ready to learn</p>
            <h1>从内容出发，而不是从设置出发。</h1>
            <p className="hero-copy">
              直接粘贴链接，或者导入本地视频与字幕。把进入学习状态的步骤压到最少。
            </p>
          </div>

          <div className="hero-stats">
            <article className="stat-card compact">
              <span>总条目</span>
              <strong>{stats.total}</strong>
            </article>
            <article className="stat-card compact">
              <span>已就绪</span>
              <strong>{stats.completed}</strong>
            </article>
            <article className="stat-card compact">
              <span>处理中</span>
              <strong>{stats.processing}</strong>
            </article>
          </div>
        </div>
      </section>

      <section className="grid-two">
        <form className="card settings-form" onSubmit={onSubmitUrl}>
          <div className="section-head">
            <div>
              <p className="eyebrow">在线导入</p>
              <h3>提交视频链接</h3>
            </div>
            <span className="subtle-text">{sourceLang} → {targetLang}</span>
          </div>

          <label className="field full-width">
            <span>YouTube 链接</span>
            <input
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="粘贴视频链接，系统会在后台拉取视频、字幕并进入学习页。"
            />
          </label>

          <button type="submit" className="primary-button" disabled={submitMutation.isPending}>
            {submitMutation.isPending ? '正在提交...' : '开始解析'}
          </button>
        </form>

        <form className="card settings-form" onSubmit={onImport}>
          <div className="section-head">
            <div>
              <p className="eyebrow">本地导入</p>
              <h3>导入 mp4/mov + srt/vtt</h3>
            </div>
            <span className="subtle-text">适配移动端和桌面端文件选择器。</span>
          </div>

          <label className="field full-width">
            <span>视频文件</span>
            <input
              type="file"
              accept=".mp4,.mov,video/mp4,video/quicktime"
              onChange={(event) => setVideoFile(event.target.files?.[0])}
            />
          </label>

          <label className="field full-width">
            <span>字幕文件</span>
            <input
              type="file"
              accept=".srt,.vtt,text/vtt,application/x-subrip"
              onChange={(event) => setSubtitleFile(event.target.files?.[0])}
            />
          </label>

          <button type="submit" className="primary-button" disabled={!videoFile && !subtitleFile}>
            {videoFile ? '保存并进入学习' : '上传字幕到后端重译'}
          </button>
        </form>
      </section>

      <section className="card">
        <div className="section-head">
          <div>
            <p className="eyebrow">继续学习</p>
            <h3>最近打开的内容</h3>
          </div>
        </div>

        {continueItems.length ? (
          <div className="task-list-grid">
            {continueItems.map((item) => (
              <article key={item.id} className="task-card status-completed">
                <div className="task-card-head">
                  <span className="status-badge">{item.meta}</span>
                </div>
                <h3>{item.title}</h3>
                <div className="task-card-foot">
                  <span />
                  <button type="button" className="text-button" onClick={() => navigate(item.path)}>
                    继续学习
                  </button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-card">还没有可继续的内容，先从上方导入一个视频或字幕开始。</div>
        )}
      </section>

      <section className="card">
        <div className="section-head">
          <div>
            <p className="eyebrow">最近在线任务</p>
            <h3>不中断的处理队列</h3>
          </div>
        </div>

        <TaskList
          tasks={(tasksQuery.data ?? []).slice(0, 4)}
          onOpen={(taskId) => navigate(`/study/backend/${taskId}`)}
        />
      </section>
    </div>
  );
}
