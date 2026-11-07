export const fixtures = [
  { id: 'cabinet', name: '机柜外观', url: '/demo-media/cabinet.svg' },
  { id: 'indicators', name: '指示灯正常', url: '/demo-media/indicators.svg' },
  { id: 'panel', name: '控制面板', url: '/demo-media/panel.svg' },
  { id: 'indicators-abnormal', name: '指示灯异常', url: '/demo-media/indicators-abnormal.svg' },
] as const;

const MAX_BYTES = 10 * 1024 * 1024;
const ALLOWED = ['image/jpeg', 'image/png', 'image/webp'];

export async function validateImage(blob: Blob): Promise<{ width: number; height: number }> {
  if (!ALLOWED.includes(blob.type)) throw new Error('仅支持 JPEG、PNG、WebP 图片，请重新选择。');
  if (blob.size > MAX_BYTES) throw new Error('图片不能超过 10MB，请选择较小的图片。');
  const bytes = new Uint8Array(await blob.slice(0, 12).arrayBuffer());
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255;
  const png = bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71 && bytes[4] === 13 && bytes[5] === 10 && bytes[6] === 26 && bytes[7] === 10;
  const webp = String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP';
  if (!(blob.type === 'image/jpeg' ? jpeg : blob.type === 'image/png' ? png : webp)) throw new Error('图片内容与格式不符或文件已损坏，请重新选择。');
  const url = URL.createObjectURL(blob);
  try {
    const image = await loadImage(url);
    return { width: image.naturalWidth, height: image.naturalHeight };
  } finally { URL.revokeObjectURL(url); }
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => image.naturalWidth && image.naturalHeight ? resolve(image) : reject(new Error('图片无法解码，请重新选择。'));
    image.onerror = () => reject(new Error('图片无法解码，请重新选择。'));
    image.src = src;
  });
}

export function canvasBlob(canvas: HTMLCanvasElement, type = 'image/png'): Promise<Blob> {
  return new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片处理失败，请重试。')), type, 0.92));
}

export async function loadFixture(id: string) {
  const fixture = fixtures.find(item => item.id === id);
  if (!fixture) throw new Error('演示素材不存在。');
  // Only bundled, allow-listed SVGs enter the canvas. User SVGs are never accepted.
  const image = await loadImage(fixture.url);
  const canvas = document.createElement('canvas');
  canvas.width = image.naturalWidth;
  canvas.height = image.naturalHeight;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('浏览器无法处理图片，请使用文件上传。');
  context.drawImage(image, 0, 0);
  const blob = await canvasBlob(canvas);
  return { blob, width: canvas.width, height: canvas.height, name: `${fixture.name}（演示素材）.png` };
}
