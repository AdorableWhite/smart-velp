import { useMutation, useQuery } from '@tanstack/react-query';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskList } from '../components/library/TaskList';
import { fetchTasks, submitAnalyze, submitLocalSubtitleAnalyze } from '../services/api/media';
import { listImportedMedia, saveImportedMedia } from '../services/storage/localMedia';
import { getTranslationProfile } from '../services/storage/translationProfiles';
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

  const onSubmitUrl = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!url.trim()) {
      return;
    }

    const selectedProfile = await getTranslationProfile(selectedProfileId);
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
    const selectedProfile = await getTranslationProfile(selectedProfileId);
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
              apiKey: selectedProfile.apiKey
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
              apiKey: selectedProfile.apiKey
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
      <section className="hero card">
        <div>
          <p className="eyebrow">Apple-inspired learning</p>
          <h1>让界面随设备自然变化，把注意力留给内容本身。</h1>
          <p className="hero-copy">
            当前版本已经按手机、平板、桌面做响应式布局。你可以直接粘贴 YouTube 链接，也可以导入本地
            mp4/mov 与 srt/vtt 文件开始学习。
          </p>
        </div>

        <div className="stats-grid">
          <article className="stat-card">
            <span>总条目</span>
            <strong>{stats.total}</strong>
          </article>
          <article className="stat-card">
            <span>已就绪</span>
            <strong>{stats.completed}</strong>
          </article>
          <article className="stat-card">
            <span>处理中</span>
            <strong>{stats.processing}</strong>
          </article>
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
            <p className="eyebrow">最近在线任务</p>
            <h3>不中断的学习队列</h3>
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
