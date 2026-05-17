import type { DisplaySubtitle, SubtitleMode } from '../../types/media';
import { saveBlobToDevice } from './download';

interface RenderedVideoExportOptions {
  videoSrc: string;
  fileName: string;
  subtitles: DisplaySubtitle[];
  subtitleMode: SubtitleMode;
  fontSize: number;
  onProgress?: (progress: number) => void;
}

const mimeTypeCandidates = [
  'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
  'video/webm;codecs=vp9,opus',
  'video/webm;codecs=vp8,opus',
  'video/webm'
];

function pickMimeType() {
  return mimeTypeCandidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? '';
}

function extensionForMimeType(mimeType: string) {
  return mimeType.includes('mp4') ? 'mp4' : 'webm';
}

function visibleSubtitle(mode: SubtitleMode, subtitle?: DisplaySubtitle) {
  if (!subtitle || mode === 'hidden') {
    return [];
  }

  if (mode === 'source') {
    return subtitle.sourceText ? [{ text: subtitle.sourceText, weight: 800 }] : [];
  }

  if (mode === 'target') {
    return subtitle.targetText ? [{ text: subtitle.targetText, weight: 700 }] : [];
  }

  return [
    subtitle.sourceText ? { text: subtitle.sourceText, weight: 800 } : undefined,
    subtitle.targetText ? { text: subtitle.targetText, weight: 700 } : undefined
  ].filter(Boolean) as Array<{ text: string; weight: number }>;
}

function wrapText(context: CanvasRenderingContext2D, text: string, maxWidth: number) {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = '';

  for (const word of words) {
    const candidate = line ? `${line} ${word}` : word;
    if (context.measureText(candidate).width <= maxWidth || !line) {
      line = candidate;
    } else {
      lines.push(line);
      line = word;
    }
  }

  if (line) {
    lines.push(line);
  }

  return lines;
}

function roundedRect(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number
) {
  context.beginPath();
  context.moveTo(x + radius, y);
  context.lineTo(x + width - radius, y);
  context.quadraticCurveTo(x + width, y, x + width, y + radius);
  context.lineTo(x + width, y + height - radius);
  context.quadraticCurveTo(x + width, y + height, x + width - radius, y + height);
  context.lineTo(x + radius, y + height);
  context.quadraticCurveTo(x, y + height, x, y + height - radius);
  context.lineTo(x, y + radius);
  context.quadraticCurveTo(x, y, x + radius, y);
  context.closePath();
}

function drawSubtitles(
  context: CanvasRenderingContext2D,
  canvas: HTMLCanvasElement,
  lines: Array<{ text: string; weight: number }>,
  fontSize: number
) {
  if (!lines.length) {
    return;
  }

  const scaledFontSize = Math.max(18, Math.round(fontSize * (canvas.width / 1280)));
  const maxWidth = canvas.width * 0.84;
  const lineHeight = scaledFontSize * 1.34;
  const wrappedLines = lines.flatMap((line) => {
    context.font = `${line.weight} ${scaledFontSize}px "Microsoft YaHei", "PingFang SC", Arial, sans-serif`;
    return wrapText(context, line.text, maxWidth).map((text) => ({ text, weight: line.weight }));
  });

  const blockHeight = wrappedLines.length * lineHeight;
  const paddingX = scaledFontSize * 0.8;
  const paddingY = scaledFontSize * 0.55;
  const boxWidth = Math.min(canvas.width * 0.9, maxWidth + paddingX * 2);
  const boxX = (canvas.width - boxWidth) / 2;
  const boxY = canvas.height - blockHeight - paddingY * 2 - canvas.height * 0.07;

  context.fillStyle = 'rgba(0, 0, 0, 0.58)';
  roundedRect(context, boxX, boxY, boxWidth, blockHeight + paddingY * 2, scaledFontSize * 0.45);
  context.fill();

  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.shadowColor = 'rgba(0, 0, 0, 0.82)';
  context.shadowBlur = 8;
  context.fillStyle = '#ffffff';

  wrappedLines.forEach((line, index) => {
    context.font = `${line.weight} ${scaledFontSize}px "Microsoft YaHei", "PingFang SC", Arial, sans-serif`;
    context.fillText(line.text, canvas.width / 2, boxY + paddingY + lineHeight * index + lineHeight / 2, maxWidth);
  });

  context.shadowBlur = 0;
}

function subtitleAt(subtitles: DisplaySubtitle[], time: number) {
  return subtitles.find((subtitle) => time >= subtitle.startTime && time < subtitle.endTime);
}

async function waitForMetadata(video: HTMLVideoElement) {
  if (video.readyState >= HTMLMediaElement.HAVE_METADATA && Number.isFinite(video.duration)) {
    return;
  }

  await new Promise<void>((resolve, reject) => {
    video.onloadedmetadata = () => resolve();
    video.onerror = () => reject(new Error('视频元数据加载失败'));
  });
}

async function createAudioStream(video: HTMLVideoElement) {
  const AudioContextCtor = window.AudioContext || (window as Window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextCtor) {
    return { stream: undefined, cleanup: () => undefined };
  }

  try {
    const audioContext = new AudioContextCtor();
    const source = audioContext.createMediaElementSource(video);
    const destination = audioContext.createMediaStreamDestination();
    source.connect(destination);
    await audioContext.resume();
    return {
      stream: destination.stream,
      cleanup: () => {
        source.disconnect();
        void audioContext.close();
      }
    };
  } catch {
    return { stream: undefined, cleanup: () => undefined };
  }
}

export async function exportRenderedVideo(options: RenderedVideoExportOptions) {
  if (!('MediaRecorder' in window)) {
    throw new Error('当前浏览器不支持视频渲染导出');
  }

  const mimeType = pickMimeType();
  if (!mimeType) {
    throw new Error('当前浏览器不支持可用的视频导出格式');
  }

  const sourceVideo = document.createElement('video');
  sourceVideo.crossOrigin = options.videoSrc.startsWith('blob:') ? null : 'anonymous';
  sourceVideo.src = options.videoSrc;
  sourceVideo.playsInline = true;
  sourceVideo.preload = 'auto';
  sourceVideo.playbackRate = 1;
  sourceVideo.muted = false;

  await waitForMetadata(sourceVideo);
  if (!Number.isFinite(sourceVideo.duration) || sourceVideo.duration <= 0) {
    throw new Error('视频时长不可用，无法渲染导出');
  }

  const canvas = document.createElement('canvas');
  canvas.width = sourceVideo.videoWidth || 1280;
  canvas.height = sourceVideo.videoHeight || Math.round(canvas.width * 9 / 16);
  const context = canvas.getContext('2d');
  if (!context) {
    throw new Error('无法创建视频渲染画布');
  }

  const canvasStream = canvas.captureStream(30);
  const audio = await createAudioStream(sourceVideo);
  const outputStream = new MediaStream([
    ...canvasStream.getVideoTracks(),
    ...(audio.stream?.getAudioTracks() ?? [])
  ]);
  const recorder = new MediaRecorder(outputStream, { mimeType });
  const chunks: BlobPart[] = [];

  const finished = new Promise<Blob>((resolve, reject) => {
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    };
    recorder.onerror = () => reject(new Error('视频录制失败'));
    recorder.onstop = () => resolve(new Blob(chunks, { type: mimeType }));
  });

  let animationFrame = 0;
  const drawFrame = () => {
    context.fillStyle = '#000000';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(sourceVideo, 0, 0, canvas.width, canvas.height);
    drawSubtitles(
      context,
      canvas,
      visibleSubtitle(options.subtitleMode, subtitleAt(options.subtitles, sourceVideo.currentTime)),
      options.fontSize
    );
    options.onProgress?.(Math.min(0.99, sourceVideo.currentTime / sourceVideo.duration));

    if (!sourceVideo.ended && recorder.state === 'recording') {
      animationFrame = requestAnimationFrame(drawFrame);
    }
  };

  try {
    sourceVideo.currentTime = 0;
    recorder.start(1000);
    await sourceVideo.play();
    drawFrame();

    await new Promise<void>((resolve) => {
      sourceVideo.onended = () => resolve();
    });

    if (recorder.state === 'recording') {
      recorder.stop();
    }

    const blob = await finished;
    options.onProgress?.(1);
    const extension = extensionForMimeType(mimeType);
    const fileName = options.fileName.replace(/\.[^.]+$/, '') + `-字幕版.${extension}`;
    await saveBlobToDevice(blob, fileName, mimeType);
  } finally {
    if (animationFrame) {
      cancelAnimationFrame(animationFrame);
    }
    sourceVideo.pause();
    sourceVideo.removeAttribute('src');
    sourceVideo.load();
    outputStream.getTracks().forEach((track) => track.stop());
    audio.cleanup();
  }
}
