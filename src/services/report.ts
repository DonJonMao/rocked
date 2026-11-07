import type { AppState, Task } from '../domain/types';

export function stats(task: Task) {
  const completed = task.stepRecords.filter((record) => record.status === 'completed').length;
  const total = task.stepRecords.length;
  const photos = task.stepRecords.reduce((sum, record) => sum + record.photoIds.length, 0);
  const abnormal = task.stepRecords.filter((record) => record.status === 'completed' && record.draft.result === 'abnormal').length;
  return { completed, total, photos, abnormal, percent: total ? Math.round(completed / total * 100) : 0 };
}

export function reportConclusion(task: Task): string {
  if (stats(task).abnormal > 0) return '发现异常';
  return task.status === 'completed' ? '未发现异常' : '巡检尚未完成';
}

/** Export durable data only. Blob bytes stay in IndexedDB; URLs are never persisted. */
export function exportRecord(state: AppState, task: Task) {
  return {
    schemaVersion: 1,
    task: structuredClone(task),
    statistics: stats(task),
    conclusion: reportConclusion(task),
    photos: state.photos.filter((photo) => photo.taskId === task.id).map((photo) => ({
      id: photo.id, taskId: photo.taskId, stepId: photo.stepId,
      source: photo.source, isDemo: photo.isDemo, name: photo.name,
      width: photo.width, height: photo.height, addedAt: photo.addedAt,
      mimeType: photo.blob.type, size: photo.blob.size,
    })),
    events: state.events.filter((event) => event.taskId === task.id).map((event) => ({ ...event })),
  };
}
