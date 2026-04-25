import type { ImportedMediaRecord, ImportedMediaSummary } from '../../types/media';
import { db } from './db';

export async function saveImportedMedia(videoFile: File, subtitleFile?: File, subtitleTaskId?: string) {
  const id = crypto.randomUUID();
  const title = videoFile.name.replace(/\.[^/.]+$/, '');
  const record: ImportedMediaRecord = {
    id,
    title,
    videoName: videoFile.name,
    subtitleName: subtitleFile?.name,
    videoFile,
    subtitleFile,
    subtitleTaskId,
    createdAt: Date.now()
  };

  await db.importedMedia.put(record);
  return id;
}

export async function getImportedMedia(id: string) {
  return db.importedMedia.get(id);
}

export async function updateImportedMediaSubtitleTask(id: string, subtitleTaskId?: string) {
  const existing = await db.importedMedia.get(id);
  if (!existing) {
    return;
  }

  await db.importedMedia.put({
    ...existing,
    subtitleTaskId
  });
}

export async function listImportedMedia(): Promise<ImportedMediaSummary[]> {
  const rows = await db.importedMedia.orderBy('createdAt').reverse().toArray();
  return rows.map((row) => ({
    id: row.id,
    title: row.title,
    videoName: row.videoName,
    subtitleName: row.subtitleName,
    subtitleTaskId: row.subtitleTaskId,
    createdAt: row.createdAt
  }));
}
