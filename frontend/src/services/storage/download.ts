export async function downloadToDevice(downloadUrl: string, fileName: string) {
  const safeName = fileName.replace(/[/\\?%*:|"<>]/g, '_');

  if ('showSaveFilePicker' in window) {
    try {
      const handle = await (window as Window & {
        showSaveFilePicker: (options: {
          suggestedName: string;
          types: Array<{ description: string; accept: Record<string, string[]> }>;
        }) => Promise<{
          createWritable: () => Promise<WritableStream>;
        }>;
      }).showSaveFilePicker({
        suggestedName: safeName,
        types: [
          {
            description: 'Video File',
            accept: { 'video/mp4': ['.mp4'] }
          }
        ]
      });

      const response = await fetch(downloadUrl);
      if (!response.body) {
        throw new Error('下载流不可用');
      }
      const writable = await handle.createWritable();
      await response.body.pipeTo(writable);
      return;
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }
    }
  }

  const anchor = document.createElement('a');
  anchor.href = downloadUrl;
  anchor.download = safeName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
}

