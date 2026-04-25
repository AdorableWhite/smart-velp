import type { BackendSubtitle, DisplaySubtitle } from '../types/media';

function toSeconds(raw: string) {
  const normalized = raw.replace(',', '.').trim();
  const [hours, minutes, seconds] = normalized.split(':');
  return Number(hours) * 3600 + Number(minutes) * 60 + Number(seconds);
}

function linesToSubtitle(block: string, index: number): DisplaySubtitle | null {
  const rows = block
    .split(/\r?\n/)
    .map((row) => row.trim())
    .filter(Boolean);

  if (!rows.length) {
    return null;
  }

  const timingRow = rows.find((row) => row.includes('-->'));
  if (!timingRow) {
    return null;
  }

  const textRows = rows.filter((row) => row !== timingRow && !/^\d+$/.test(row) && row !== 'WEBVTT');
  const [startRaw, endRaw] = timingRow.split('-->').map((item) => item.trim());

  return {
    id: `local-${index}`,
    startTime: toSeconds(startRaw),
    endTime: toSeconds(endRaw),
    sourceText: textRows[0] ?? '',
    targetText: textRows.slice(1).join(' '),
    sourceLang: 'en'
  };
}

export function normalizeBackendSubtitles(subtitles: BackendSubtitle[]): DisplaySubtitle[] {
  return subtitles.map((subtitle, index) => ({
    id: `remote-${index}`,
    startTime: subtitle.startTime,
    endTime: subtitle.endTime,
    sourceText: subtitle.sourceText ?? subtitle.en ?? '',
    targetText: subtitle.targetText ?? subtitle.cn ?? '',
    sourceLang: subtitle.sourceLang ?? 'en',
    targetLang: subtitle.targetLang ?? 'zh-CN'
  }));
}

export async function parseSubtitleFile(file?: Blob) {
  if (!file) {
    return [] as DisplaySubtitle[];
  }

  const text = await file.text();
  const blocks = text
    .replace(/\r/g, '')
    .split(/\n{2,}/)
    .map((block) => block.trim())
    .filter(Boolean);

  return blocks
    .map((block, index) => linesToSubtitle(block, index))
    .filter((item): item is DisplaySubtitle => Boolean(item));
}
