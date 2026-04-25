import type { TaskSummary } from '../../types/media';

const labels: Record<string, string> = {
  pending: '等待中',
  processing: '处理中',
  completed: '已完成',
  failed: '失败'
};

interface TaskListProps {
  tasks: TaskSummary[];
  onOpen: (taskId: string) => void;
  onDelete?: (taskId: string) => void;
}

export function TaskList({ tasks, onOpen, onDelete }: TaskListProps) {
  if (!tasks.length) {
    return <div className="empty-card">还没有在线任务，先从首页提交一个视频链接吧。</div>;
  }

  return (
    <div className="task-list-grid">
      {tasks.map((task) => {
        const clickable = task.status === 'completed' || task.status === 'processing' || task.status === 'pending';

        return (
          <article key={task.taskId} className={`task-card status-${task.status}`}>
            <div className="task-card-head">
              <span className="status-badge">{labels[task.status] ?? task.status}</span>
              {onDelete ? (
                <button type="button" className="text-button danger" onClick={() => onDelete(task.taskId)}>
                  删除
                </button>
              ) : null}
            </div>

            <h3>{task.title || task.url}</h3>
            <p className="task-url">{task.url}</p>

            <div className="progress-strip" aria-hidden="true">
              <span style={{ width: `${Math.max(task.progress || 0, task.status === 'completed' ? 100 : 4)}%` }} />
            </div>

            <div className="task-card-foot">
              <span>{task.progress || 0}%</span>
              <button
                type="button"
                className="text-button"
                disabled={!clickable}
                onClick={() => onOpen(task.taskId)}
              >
                {task.status === 'completed' ? '进入学习' : '查看进度'}
              </button>
            </div>
          </article>
        );
      })}
    </div>
  );
}
