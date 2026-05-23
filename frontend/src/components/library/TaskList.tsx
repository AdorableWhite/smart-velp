import type { TaskSummary } from '../../types/media';

const labels: Record<string, string> = {
  pending: '等待中',
  processing: '处理中',
  completed: '已完成',
  failed: '失败'
};

function formatTaskError(error: string) {
  const [diagnosis, rawSummary] = error.split('\n\n原始摘要：');
  return {
    diagnosis: diagnosis.trim(),
    rawSummary: rawSummary?.trim()
  };
}

interface TaskListProps {
  tasks: TaskSummary[];
  onOpen: (taskId: string) => void;
  onDelete?: (taskId: string) => void;
  onRetry?: (task: TaskSummary) => void;
}

export function TaskList({ tasks, onOpen, onDelete, onRetry }: TaskListProps) {
  if (!tasks.length) {
    return <div className="empty-card">还没有在线任务，先从首页提交一个视频链接。</div>;
  }

  return (
    <div className="task-list-grid">
      {tasks.map((task) => {
        const clickable = task.status === 'completed' || task.status === 'processing' || task.status === 'pending';
        const formattedError = task.error ? formatTaskError(task.error) : undefined;
        const assetMissing = task.status === 'completed' && task.assetAvailable === false;
        const retryable = onRetry && !task.url.startsWith('local://') && !task.url.startsWith('course://');

        return (
          <article key={task.taskId} className={`task-card status-${task.status}${assetMissing ? ' asset-missing' : ''}`}>
            <div className="task-card-head">
              <span className="status-badge">{assetMissing ? '资源缺失' : labels[task.status] ?? task.status}</span>
              {onDelete ? (
                <button type="button" className="text-button danger" onClick={() => onDelete(task.taskId)}>
                  删除
                </button>
              ) : null}
            </div>

            <h3>{task.title || task.url}</h3>
            <p className="task-url">{task.url}</p>

            {task.status === 'failed' && formattedError ? (
              <div className="task-error" title={task.error ?? undefined}>
                <strong>{formattedError.diagnosis}</strong>
                {formattedError.rawSummary ? <span>{formattedError.rawSummary}</span> : null}
              </div>
            ) : null}
            {assetMissing ? (
              <div className="task-error task-warning" title={task.assetMessage ?? undefined}>
                <strong>{task.assetMessage ?? '课程资源缺失，需要重新解析。'}</strong>
                <span>可以重新提交原链接来恢复视频和字幕。</span>
              </div>
            ) : null}

            <div className="progress-strip" aria-hidden="true">
              <span style={{ width: `${Math.max(task.progress || 0, task.status === 'completed' ? 100 : 4)}%` }} />
            </div>

            <div className="task-card-foot">
              <span>{task.progress || 0}%</span>
              <div className="inline-actions">
                {(task.status === 'failed' || assetMissing) && retryable ? (
                  <button type="button" className="text-button" onClick={() => onRetry(task)}>
                    {assetMissing ? '重新提交' : '重试'}
                  </button>
                ) : null}
                <button
                  type="button"
                  className="text-button"
                  disabled={!clickable || assetMissing}
                  onClick={() => onOpen(task.taskId)}
                >
                  {assetMissing ? '等待恢复' : task.status === 'completed' ? '进入学习' : '查看进度'}
                </button>
              </div>
            </div>
          </article>
        );
      })}
    </div>
  );
}
