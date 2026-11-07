import { expect, test, type Page } from '@playwright/test';
import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import type { AuditEvent, EvidencePhoto, Task } from '../../src/domain/types';

const screenshots = path.resolve('artifacts/screenshots');
const fixture = (file: string) => path.resolve('tests/fixtures', file);
const current = (page: Page) => page.getByTestId('current-step');

async function open(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('button', { name: /^下发(?:新)?任务$/ })).toBeVisible();
}
async function dispatch(page: Page) {
  await page.getByRole('button', { name: /^下发(?:新)?任务$/ }).click();
  await expect(page.getByRole('button', { name: '开始巡检', exact: true })).toBeVisible();
}
async function start(page: Page) {
  await dispatch(page);
  await page.getByRole('button', { name: '开始巡检', exact: true }).click();
  await page.getByRole('button', { name: '模拟扫描当前设备', exact: true }).click();
  await page.getByRole('checkbox', { name: '我已核对设备名称与位置' }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(current(page)).toContainText('检查柜门与外观');
}
async function photo(page: Page, choice?: string) {
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  if (choice) await dialog.getByRole('button', { name: choice, exact: true }).click();
  await dialog.getByRole('button', { name: '使用演示图片', exact: true }).click();
  await expect(dialog).not.toBeVisible();
}
async function completePhotoStep(page: Page, abnormal = false) {
  await photo(page, abnormal ? '指示灯异常' : undefined);
  await page.getByRole('radio', { name: abnormal ? '异常' : '正常', exact: true }).check();
  if (abnormal) await page.getByRole('textbox', { name: '异常说明（必填）', exact: true }).fill('面板故障指示灯亮，已记录并提交复核');
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
}
async function finish(page: Page) {
  await page.getByRole('checkbox', { name: '我已核对以上记录' }).check();
  await page.getByRole('button', { name: '提交巡检记录', exact: true }).click();
  await expect(page.getByRole('button', { name: '查看巡检报告', exact: true }).first()).toBeVisible();
}
async function fullTask(page: Page, abnormal = false) {
  await start(page);
  await completePhotoStep(page);
  await completePhotoStep(page, abnormal);
  await completePhotoStep(page);
  await finish(page);
}
async function shot(page: Page, name: string) {
  await mkdir(screenshots, { recursive: true });
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(() => page.locator('img').evaluateAll(images => images.every(item => (item as HTMLImageElement).complete && (item as HTMLImageElement).naturalWidth > 0))).toBe(true);
  await page.screenshot({ path: path.join(screenshots, name), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}
async function db(page: Page): Promise<{ tasks: Task[]; photos: EvidencePhoto[]; events: AuditEvent[]; stores: Record<string, unknown[]> }> {
  return page.evaluate(async () => {
    const databases = await indexedDB.databases();
    const name = databases.find(item => item.name)?.name;
    if (!name) throw new Error('No inspection database found');
    const database = await new Promise<IDBDatabase>((resolve, reject) => { const request = indexedDB.open(name); request.onsuccess = () => resolve(request.result); request.onerror = () => reject(request.error); });
    const names = Array.from(database.objectStoreNames);
    const stores: Record<string, unknown[]> = {};
    await Promise.all(names.map(storeName => new Promise<void>((resolve, reject) => {
      const request = database.transaction(storeName, 'readonly').objectStore(storeName).getAll();
      request.onsuccess = () => { stores[storeName] = request.result; resolve(); }; request.onerror = () => reject(request.error);
    })));
    database.close();
    return { tasks: [...(stores.tasks ?? [])].sort((a, b) => (a as { createdAt: string }).createdAt.localeCompare((b as { createdAt: string }).createdAt)), photos: stores.photos ?? [], events: stores.events ?? [], stores };
  }) as Promise<{ tasks: Task[]; photos: EvidencePhoto[]; events: AuditEvent[]; stores: Record<string, unknown[]> }>;
}

for (const viewport of [{ width: 1366, height: 768 }, { width: 1440, height: 900 }, { width: 1920, height: 1080 }]) {
  test(`完整流程、异常、暂停恢复与报告 ${viewport.width}×${viewport.height}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const errors: string[] = [];
    const external: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/*', route => {
      if (!route.request().url().startsWith('http://127.0.0.1:5173/')) { external.push(route.request().url()); return route.abort(); }
      return route.continue();
    });
    await open(page);
    await shot(page, `${viewport.width}-01-initial.png`);
    await start(page);
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
    await expect(page.getByText('请先拍摄或选择至少 1 张照片', { exact: false })).toBeVisible();
    await expect(current(page)).toContainText('检查柜门与外观');
    await shot(page, `${viewport.width}-02-missing-photo.png`);
    await expect(page.getByText('请先拍摄或选择至少 1 张照片', { exact: false })).toBeInViewport();
    await expect(page.getByRole('button', { name: '完成本步', exact: true })).toBeInViewport({ ratio: 1 });
    await completePhotoStep(page);
    await expect(current(page)).toContainText('检查设备指示灯');
    await photo(page, '指示灯异常');
    await page.getByRole('radio', { name: '异常', exact: true }).check();
    await page.getByRole('textbox', { name: '异常说明（必填）', exact: true }).fill('面板故障指示灯亮，已记录并提交复核');
    await shot(page, `${viewport.width}-03-running.png`);
    await expect(current(page).getByRole('heading', { name: '检查设备指示灯', exact: true })).toBeInViewport({ ratio: 1 });
    await expect(page.getByRole('button', { name: '完成本步', exact: true })).toBeInViewport({ ratio: 1 });
    await page.getByRole('button', { name: '暂停巡检', exact: true }).click();
    await expect(page.getByRole('button', { name: '继续巡检', exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('button', { name: '继续巡检', exact: true })).toBeVisible();
    await shot(page, `${viewport.width}-04-paused-restored.png`);
    await page.getByRole('button', { name: '继续巡检', exact: true }).click();
    await expect(page.getByRole('textbox', { name: '异常说明（必填）', exact: true })).toHaveValue('面板故障指示灯亮，已记录并提交复核');
    await expect(page.getByRole('radio', { name: '异常', exact: true })).toBeChecked();
    await expect(current(page).locator('img').first()).toBeVisible();
    expect(await current(page).locator('img').first().evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
    await completePhotoStep(page);
    await expect(current(page)).toContainText('提交巡检记录');
    await page.getByRole('button', { name: '提交巡检记录', exact: true }).click();
    await expect(page.getByText('请勾选', { exact: false }).first()).toBeVisible();
    await finish(page);
    const saved = await db(page);
    expect(saved.tasks).toHaveLength(1);
    expect(saved.tasks[0].status).toBe('completed');
    expect(saved.tasks[0].stepRecords.filter(item => item.status === 'completed')).toHaveLength(5);
    expect(saved.photos).toHaveLength(3);
    expect(saved.photos.every(item => item.source === 'fixture' && item.isDemo && item.taskId === saved.tasks[0].id)).toBe(true);
    await page.getByRole('button', { name: '查看巡检报告', exact: true }).first().click();
    await expect(page.getByText('发现异常', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('面板故障指示灯亮，已记录并提交复核', { exact: false }).first()).toBeVisible();
    await shot(page, `${viewport.width}-05-completed-report.png`);
    expect(external).toEqual([]);
    expect(errors).toEqual([]);
  });
}

test('校验资产、结果和异常说明，删除照片重新阻断，键盘不能跳步', async ({ page }) => {
  await open(page);
  await dispatch(page);
  await page.reload();
  await expect(page.getByRole('button', { name: '开始巡检', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '开始巡检', exact: true }).click();
  await page.getByLabel('设备资产码', { exact: true }).fill('WRONG-ASSET');
  await page.getByRole('checkbox', { name: '我已核对设备名称与位置' }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByRole('alert').last()).toContainText('ASSET-A03');
  await expect(current(page)).toContainText('确认巡检设备');
  await page.getByRole('button', { name: '模拟扫描当前设备', exact: true }).click();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  const lockedStep = page.getByRole('button', { name: '检查控制面板 待执行', exact: true });
  await expect(lockedStep).toBeDisabled();
  await lockedStep.dispatchEvent('click');
  await page.getByLabel('眼镜执行视窗', { exact: true }).focus();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('ArrowRight');
  await expect(current(page)).toContainText('检查柜门与外观');
  await photo(page);
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByText('请选择检查结果', { exact: false })).toBeVisible();
  await page.getByRole('radio', { name: '异常', exact: true }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByText('请填写异常说明', { exact: false })).toBeVisible();
  await page.getByRole('textbox', { name: '异常说明（必填）', exact: true }).fill('柜门边缘有划痕');
  await page.getByRole('textbox', { name: '异常说明（必填）', exact: true }).press('ArrowDown');
  await page.getByRole('textbox', { name: '异常说明（必填）', exact: true }).press('Enter');
  await expect(current(page)).toContainText('检查柜门与外观');
  await page.getByRole('button', { name: /删除照片/ }).first().click();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByText('请先拍摄或选择至少 1 张照片', { exact: false })).toBeVisible();
  await photo(page);
  const complete = page.getByRole('button', { name: '完成本步', exact: true });
  await complete.evaluate(element => { (element as HTMLButtonElement).click(); (element as HTMLButtonElement).click(); });
  await expect(current(page)).toContainText('检查设备指示灯');
  const saved = await db(page);
  expect(saved.tasks[0].stepRecords.filter(item => item.status === 'completed')).toHaveLength(2);
  expect(saved.events.filter(item => item.type === 'step_completed' && item.stepId === 'check-cabinet')).toHaveLength(1);
  await page.getByRole('button', { name: '检查柜门与外观 已完成，查看记录', exact: true }).click();
  const detail = page.getByRole('dialog', { name: '检查柜门与外观' });
  await expect(detail).toContainText('已完成 · 只读');
  await expect(detail.locator('input, textarea, select')).toHaveCount(0);
  await expect(detail.getByRole('button', { name: /删除照片/ })).toHaveCount(0);
  await expect(detail.locator('img')).toHaveCount(1);
  await page.keyboard.press('Escape');
  await expect(detail).not.toBeVisible();
});

test('上传三种图片、拒绝错误格式/损坏/超限文件，刷新后 Blob 图片可见', async ({ page }) => {
  await open(page); await start(page);
  for (const extension of ['png', 'jpg', 'webp']) {
    await page.getByRole('button', { name: '采集照片', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '采集巡检照片' });
    await dialog.getByLabel('选择本地图片').setInputFiles(fixture(`cabinet.${extension}`));
    await expect(dialog).not.toBeVisible();
  }
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await dialog.getByLabel('选择本地图片').setInputFiles(fixture('invalid.txt'));
  await expect(dialog.getByRole('alert')).toContainText('仅支持 JPEG、PNG、WebP');
  await dialog.getByLabel('选择本地图片').setInputFiles(fixture('invalid.png'));
  await expect(dialog.getByRole('alert')).toContainText(/已损坏|无法解码/);
  await dialog.getByLabel('选择本地图片').setInputFiles(fixture('undecodable.png'));
  await expect(dialog.getByRole('alert')).toContainText('图片无法解码');
  await dialog.getByLabel('选择本地图片').setInputFiles({ name: 'too-large.png', mimeType: 'image/png', buffer: Buffer.alloc(10 * 1024 * 1024 + 1) });
  await expect(dialog.getByRole('alert')).toContainText('图片不能超过 10MB');
  await dialog.getByRole('button', { name: '关闭弹窗', exact: true }).focus();
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate(element => element.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(page.getByRole('button', { name: '采集照片', exact: true })).toBeFocused();
  await page.reload();
  await expect(current(page).locator('img')).toHaveCount(3);
  expect(await current(page).locator('img').evaluateAll(images => images.every(item => (item as HTMLImageElement).complete && (item as HTMLImageElement).naturalWidth > 0))).toBe(true);
  const saved = await db(page);
  expect(saved.photos).toHaveLength(3);
  expect(saved.photos.every(item => item.source === 'upload' && !item.isDemo)).toBe(true);
});

test('摄像头拒绝权限后保留替代入口，不重复请求权限', async ({ page }) => {
  await page.addInitScript(() => {
    let requests = 0;
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { value: async () => { requests++; (window as unknown as { cameraRequests: number }).cameraRequests = requests; throw new DOMException('Permission denied', 'NotAllowedError'); } });
  });
  await open(page); await start(page);
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await dialog.getByRole('button', { name: '开启摄像头', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('无法访问摄像头');
  await dialog.getByRole('button', { name: '使用演示图片', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { cameraRequests: number }).cameraRequests)).toBe(1);
  await page.getByRole('radio', { name: '正常', exact: true }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(current(page)).toContainText('检查设备指示灯');
});

test('照片及步骤写入失败不推进，重试后原子保存', async ({ page }) => {
  await open(page); await start(page);
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  await page.evaluate(() => {
    const original = IDBObjectStore.prototype.put;
    let fail = true;
    IDBObjectStore.prototype.put = function (...args) {
      if (fail && this.name === 'photos') { fail = false; throw new DOMException('Test quota exhaustion', 'QuotaExceededError'); }
      return original.apply(this, args);
    };
  });
  const dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await dialog.getByRole('button', { name: '使用演示图片', exact: true }).click();
  await expect(dialog.getByRole('alert')).toContainText('保存失败');
  let saved = await db(page);
  expect(saved.photos).toHaveLength(0);
  expect(saved.tasks[0].stepRecords[1].photoIds).toHaveLength(0);
  expect(saved.events.filter(item => item.type === 'photo_added')).toHaveLength(0);
  await dialog.getByRole('button', { name: '重试保存照片' }).click();
  await expect(dialog).not.toBeVisible();
  await page.getByRole('radio', { name: '正常', exact: true }).check();
  await expect.poll(async () => (await db(page)).tasks[0].stepRecords[1].draft.result).toBe('normal');
  await page.evaluate(() => {
    const original = IDBObjectStore.prototype.put;
    let fail = true;
    IDBObjectStore.prototype.put = function (...args) {
      if (fail && this.name === 'events') { fail = false; throw new DOMException('Test quota exhaustion', 'QuotaExceededError'); }
      return original.apply(this, args);
    };
  });
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByRole('alert').last()).toContainText('保存失败');
  await expect(current(page)).toContainText('检查柜门与外观');
  saved = await db(page);
  expect(saved.tasks[0].currentStepId).toBe('check-cabinet');
  expect(saved.tasks[0].stepRecords[1].status).toBe('current');
  expect(saved.events.filter(item => item.type === 'step_completed' && item.stepId === 'check-cabinet')).toHaveLength(0);
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(current(page)).toContainText('检查设备指示灯');
  saved = await db(page);
  expect(saved.photos).toHaveLength(1);
  expect(saved.events.filter(item => item.type === 'step_completed' && item.stepId === 'check-cabinet')).toHaveLength(1);
});

test('关闭采集后迟到图片不写入新步骤，迟到相机授权释放 tracks', async ({ page }) => {
  await page.addInitScript(() => {
    const testWindow = window as unknown as { delayPhoto: boolean; releasePhoto?: () => void; resolveCamera?: (value: MediaStream) => void; stopped: number };
    testWindow.stopped = 0;
    const original = HTMLCanvasElement.prototype.toBlob;
    HTMLCanvasElement.prototype.toBlob = function (callback, type, quality) {
      if (testWindow.delayPhoto) {
        testWindow.delayPhoto = false;
        return original.call(this, blob => { testWindow.releasePhoto = () => callback(blob); }, type, quality);
      }
      return original.call(this, callback, type, quality);
    };
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { value: () => new Promise<MediaStream>(resolve => { testWindow.resolveCamera = resolve; }) });
  });
  await open(page); await start(page);
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  let dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await page.evaluate(() => { (window as unknown as { delayPhoto: boolean }).delayPhoto = true; });
  await dialog.getByRole('button', { name: '使用演示图片', exact: true }).click();
  await expect.poll(() => page.evaluate(() => !!(window as unknown as { releasePhoto?: () => void }).releasePhoto)).toBe(true);
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await dialog.getByLabel('选择本地图片').setInputFiles(fixture('cabinet.png'));
  await expect(dialog).not.toBeVisible();
  await page.getByRole('radio', { name: '正常', exact: true }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(current(page)).toContainText('检查设备指示灯');
  await page.evaluate(() => (window as unknown as { releasePhoto: () => void }).releasePhoto());
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await dialog.getByRole('button', { name: '开启摄像头', exact: true }).click();
  await expect(dialog.getByRole('button', { name: '等待摄像头授权…', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await page.evaluate(() => {
    const testWindow = window as unknown as { resolveCamera: (value: MediaStream) => void; stopped: number };
    testWindow.resolveCamera({ getTracks: () => [{ stop: () => { testWindow.stopped++; } }] } as unknown as MediaStream);
  });
  await expect.poll(() => page.evaluate(() => (window as unknown as { stopped: number }).stopped)).toBe(1);
  const saved = await db(page);
  expect(saved.photos).toHaveLength(1);
  expect(saved.photos[0].stepId).toBe('check-cabinet');
  expect(saved.tasks[0].stepRecords[2].photoIds).toHaveLength(0);
});

test('模拟摄像头拍照成功，保存后停止所有 tracks', async ({ page }) => {
  await page.addInitScript(() => {
    // A real MediaStream from a deterministic canvas exercises video + capture without camera hardware.
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { value: async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 640; canvas.height = 480;
      const context = canvas.getContext('2d')!;
      const stream = canvas.captureStream(10);
      context.fillStyle = '#23425f'; context.fillRect(0, 0, 640, 480);
      context.fillStyle = 'white'; context.font = '28px sans-serif'; context.fillText('CAMERA TEST FRAME', 40, 120);
      (window as unknown as { cameraStream: MediaStream }).cameraStream = stream;
      return stream;
    } });
  });
  await open(page); await start(page);
  await page.getByRole('button', { name: '采集照片', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '采集巡检照片' });
  await dialog.getByRole('button', { name: '开启摄像头', exact: true }).click();
  await expect(dialog.locator('video')).toBeVisible();
  await expect.poll(() => dialog.locator('video').evaluate((video: HTMLVideoElement) => video.videoWidth)).toBeGreaterThan(0);
  await dialog.getByRole('button', { name: '拍照并保存', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { cameraStream: MediaStream }).cameraStream.getTracks().every(track => track.readyState === 'ended'))).toBe(true);
  const saved = await db(page);
  expect(saved.photos).toHaveLength(1);
  expect(saved.photos[0].source).toBe('camera');
  expect(saved.photos[0].isDemo).toBe(false);
});

test('模板快照、内部顺序与通用新增检查项，新任务执行新版本', async ({ page }) => {
  await open(page); await start(page);
  await page.getByRole('button', { name: '流程模板', exact: true }).click();
  await page.getByLabel('第 3 步名称', { exact: true }).fill('检查电源指示灯（新版）');
  await page.getByLabel('第 3 步照片数量', { exact: true }).fill('2');
  await page.getByRole('button', { name: '新增检查项', exact: true }).click();
  await page.getByLabel('第 5 步名称', { exact: true }).fill('检查散热通道');
  await page.getByLabel('第 5 步照片数量', { exact: true }).fill('0');
  await page.getByLabel('第 5 步必填检查结果', { exact: true }).uncheck();
  await page.getByRole('button', { name: '上移第 5 步', exact: true }).click();
  await expect(page.getByLabel('第 4 步名称', { exact: true })).toHaveValue('检查散热通道');
  await page.getByRole('button', { name: '下移第 4 步', exact: true }).click();
  await page.getByRole('button', { name: '新增检查项', exact: true }).click();
  await page.getByRole('button', { name: '删除第 6 步', exact: true }).click();
  await expect(page.getByLabel('第 1 步名称', { exact: true })).toHaveValue('确认巡检设备');
  await expect(page.getByLabel('第 6 步名称', { exact: true })).toHaveValue('提交巡检记录');
  await page.getByRole('button', { name: '保存模板', exact: true }).click();
  await expect(page.getByText('模板已本地保存为 v2，只影响后续下发任务。', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '演示工作台', exact: true }).click();
  await completePhotoStep(page);
  await expect(current(page)).toContainText('检查设备指示灯');
  await completePhotoStep(page);
  await completePhotoStep(page);
  await finish(page);
  let saved = await db(page);
  expect(saved.tasks[0].templateSnapshot.version).toBe(1);
  expect(saved.tasks[0].templateSnapshot.steps).toHaveLength(5);
  await page.getByRole('button', { name: '查看巡检报告', exact: true }).click();
  await expect(page.getByTestId('inspection-report')).toContainText('模板 v1');
  await expect(page.getByTestId('inspection-report')).not.toContainText('检查电源指示灯（新版）');
  await page.getByRole('button', { name: '返回工作台', exact: true }).click();
  await start(page);
  await completePhotoStep(page);
  await expect(current(page)).toContainText('检查电源指示灯（新版）');
  await photo(page);
  await page.getByRole('radio', { name: '正常', exact: true }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByText('请先拍摄或选择至少 2 张照片', { exact: false })).toBeVisible();
  await photo(page);
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await completePhotoStep(page);
  await expect(current(page)).toContainText('检查散热通道');
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await finish(page);
  saved = await db(page);
  expect(saved.tasks).toHaveLength(2);
  expect(saved.tasks[1].templateSnapshot.version).toBe(2);
  expect(saved.tasks[1].templateSnapshot.steps).toHaveLength(6);
  expect(saved.photos.filter(item => item.taskId === saved.tasks[0].id)).toHaveLength(3);
  expect(saved.photos.filter(item => item.taskId === saved.tasks[1].id)).toHaveLength(4);
});

test('报告 JSON、打印、历史只读和多任务隔离', async ({ page }) => {
  await open(page); await fullTask(page, true);
  const firstSaved = await db(page);
  const first = firstSaved.tasks[0];
  const firstEvents = firstSaved.events.filter(event => event.taskId === first.id).sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0) || a.occurredAt.localeCompare(b.occurredAt));
  await page.getByRole('button', { name: '查看巡检报告', exact: true }).first().click();
  const report = page.getByTestId('inspection-report');
  await expect(report).toContainText('机柜 A03');
  await expect(report).toContainText('张工（演示）');
  await expect(report.locator('img')).toHaveCount(3);
  await expect(report.locator('.report-step')).toHaveCount(5);
  await expect(report).toContainText('开始时间');
  await expect(report).toContainText('完成时间');
  for (const timestamp of [first.createdAt, first.startedAt!, first.completedAt!]) {
    const display = await page.evaluate(value => new Date(value).toLocaleString('zh-CN', { hour12: false }), timestamp);
    await expect(report).toContainText(display);
  }
  await expect(report.locator('.report-audit li')).toHaveCount(firstEvents.length);
  expect(await report.locator('.report-audit li span').allTextContents()).toEqual(firstEvents.map(event => event.summary));
  const downloaded = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出 JSON', exact: true }).click();
  const download = await downloaded;
  const downloadPath = await download.path();
  expect(downloadPath).not.toBeNull();
  const text = await readFile(downloadPath!, 'utf8');
  const record = JSON.parse(text);
  expect(record.task.id).toBe(first.id);
  expect(record.statistics).toEqual({ completed: 5, total: 5, photos: 3, abnormal: 1, percent: 100 });
  expect(record.conclusion).toBe('发现异常');
  expect(record.photos).toHaveLength(3);
  expect(text).not.toContain('blob:');
  expect(record.photos.every((item: { mimeType: string; size: number; taskId: string }) => item.mimeType === 'image/png' && item.size > 0 && item.taskId === first.id)).toBe(true);
  await page.evaluate(() => { window.print = () => { (window as unknown as { printed: boolean }).printed = true; }; });
  await page.getByRole('button', { name: '打印报告', exact: true }).click();
  await expect.poll(() => page.evaluate(() => (window as unknown as { printed?: boolean }).printed)).toBe(true);
  await page.emulateMedia({ media: 'print' });
  await expect(page.getByRole('button', { name: '打印报告', exact: true })).not.toBeVisible();
  await expect(page.locator('.no-print:visible')).toHaveCount(0);
  await expect(report.locator('.report-paper')).toBeVisible();
  await page.emulateMedia({ media: 'screen' });
  await page.getByRole('button', { name: '返回工作台', exact: true }).click();
  await fullTask(page);
  await page.getByRole('button', { name: /^巡检记录/ }).click();
  await page.getByRole('button', { name: '查看报告', exact: true }).last().click();
  await expect(report).toContainText(first.id);
  await expect(report).toContainText('面板故障指示灯亮，已记录并提交复核');
  expect(await report.locator('img').count()).toBe(3);
  await expect(report.locator('.report-audit li')).toHaveCount(firstEvents.length);
  expect(await report.locator('.report-audit li span').allTextContents()).toEqual(firstEvents.map(event => event.summary));
  const saved = await db(page);
  expect(saved.tasks).toHaveLength(2);
  expect(saved.events.filter(item => item.taskId === first.id).every(item => !item.summary.includes('undefined'))).toBe(true);
  expect(saved.photos.filter(item => item.taskId === first.id)).toHaveLength(3);
  expect(saved.photos.filter(item => item.taskId !== first.id)).toHaveLength(3);
});

test('管理端可导出 Android task.dispatch 任务文件', async ({ page }) => {
  await open(page);
  await dispatch(page);
  const downloaded = page.waitForEvent('download');
  await page.getByRole('button', { name: '导出 Android task.dispatch', exact: true }).click();
  const download = await downloaded;
  const downloadPath = await download.path();
  expect(downloadPath).not.toBeNull();
  const envelope = JSON.parse(await readFile(downloadPath!, 'utf8')) as {
    type: string;
    version: number;
    taskId: string;
    payload: { template: { id: string }; steps: unknown[]; asset: { code: string } };
  };
  expect(envelope).toMatchObject({ type: 'task.dispatch', version: 1 });
  expect(envelope.taskId).toMatch(/^task-/);
  expect(envelope.payload.template.id).toBe('template-cabinet-inspection');
  expect(envelope.payload.asset.code).toBe('ASSET-A03');
  expect(envelope.payload.steps).toHaveLength(5);
});

test('重置取消保留数据，连续三轮重置不残留任务图片和事件', async ({ page }) => {
  await open(page);
  for (let round = 0; round < 3; round++) {
    await start(page); await photo(page);
    await page.getByRole('button', { name: '重新演示', exact: true }).click();
    let dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    expect((await db(page)).photos).toHaveLength(1);
    await page.getByRole('button', { name: '重新演示', exact: true }).click();
    dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: '确认重置', exact: true }).click();
    await expect(page.getByRole('button', { name: /^下发(?:新)?任务$/ })).toBeEnabled();
    const saved = await db(page);
    expect(saved.tasks).toHaveLength(0);
    expect(saved.photos).toHaveLength(0);
    expect(saved.events).toHaveLength(0);
    expect(saved.stores.configuration).toHaveLength(1);
    const config = saved.stores.configuration[0] as { template: { version: number; steps: unknown[] }; assets: unknown[] };
    expect(config.template.version).toBe(1);
    expect(config.template.steps).toHaveLength(5);
    expect(config.assets).toHaveLength(33);
    await page.reload();
    await expect(page.getByRole('button', { name: /^下发(?:新)?任务$/ })).toBeEnabled();
  }
});

test('首末固定步骤的照片与结果要求也由配置驱动', async ({ page }) => {
  await open(page);
  await page.getByRole('button', { name: '流程模板', exact: true }).click();
  for (const index of [1, 5]) {
    await page.getByLabel(`第 ${index} 步照片数量`, { exact: true }).fill('1');
    await page.getByLabel(`第 ${index} 步必填检查结果`, { exact: true }).check();
  }
  await page.getByRole('button', { name: '保存模板', exact: true }).click();
  await expect(page.getByText('模板已本地保存为 v2，只影响后续下发任务。', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '演示工作台', exact: true }).click();
  await dispatch(page);
  await page.getByRole('button', { name: '开始巡检', exact: true }).click();
  await page.getByRole('button', { name: '模拟扫描当前设备', exact: true }).click();
  await page.getByRole('checkbox', { name: '我已核对设备名称与位置' }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(page.getByText('请先拍摄或选择至少 1 张照片', { exact: false })).toBeVisible();
  await expect(page.getByText('请选择检查结果', { exact: false })).toBeVisible();
  await completePhotoStep(page);
  await completePhotoStep(page);
  await completePhotoStep(page);
  await completePhotoStep(page);
  await expect(current(page)).toContainText('提交巡检记录');
  await page.getByRole('checkbox', { name: '我已核对以上记录' }).check();
  await page.getByRole('button', { name: '提交巡检记录', exact: true }).click();
  await expect(page.getByText('请先拍摄或选择至少 1 张照片', { exact: false })).toBeVisible();
  await photo(page);
  await page.getByRole('radio', { name: '正常', exact: true }).check();
  await page.getByRole('button', { name: '提交巡检记录', exact: true }).click();
  await expect(page.getByRole('button', { name: '查看巡检报告', exact: true })).toBeVisible();
  const saved = await db(page);
  expect(saved.tasks[0].status).toBe('completed');
  expect(saved.photos).toHaveLength(5);
  expect(saved.tasks[0].stepRecords.every(record => record.draft.result === 'normal' && record.photoIds.length === 1)).toBe(true);
});

test('非默认设备和人员下发后刷新，管理区与眼镜保持同一快照', async ({ page }) => {
  await open(page);
  await page.getByRole('combobox', { name: '目标设备', exact: true }).selectOption('asset-b01');
  await page.getByRole('combobox', { name: '执行人', exact: true }).selectOption('worker-li');
  await dispatch(page);
  await page.reload();
  await expect(page.getByRole('button', { name: '开始巡检', exact: true })).toBeVisible();
  await expect(page.getByRole('combobox', { name: '目标设备', exact: true })).toHaveValue('asset-b01');
  await expect(page.getByRole('combobox', { name: '执行人', exact: true })).toHaveValue('worker-li');
  await expect(page.getByRole('region', { name: '眼镜执行预览', exact: true })).toContainText('机柜 B01');
  await expect(page.getByRole('region', { name: '眼镜执行预览', exact: true })).toContainText('李工（演示）');
  await page.getByRole('button', { name: '开始巡检', exact: true }).click();
  await page.getByRole('button', { name: '模拟扫描当前设备', exact: true }).click();
  await expect(page.getByRole('textbox', { name: '设备资产码', exact: true })).toHaveValue('ASSET-B01');
  await page.getByRole('checkbox', { name: '我已核对设备名称与位置' }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
  await expect(current(page)).toContainText('检查柜门与外观');
  const saved = await db(page);
  expect(saved.tasks[0].assetSnapshot.code).toBe('ASSET-B01');
  expect(saved.tasks[0].assigneeSnapshot.name).toBe('李工（演示）');
});

test('原生双击不会越过连续无必填资料的检查项', async ({ page }) => {
  await open(page);
  await page.getByRole('button', { name: '流程模板', exact: true }).click();
  for (const index of [2, 3, 4]) {
    await page.getByLabel(`第 ${index} 步照片数量`, { exact: true }).fill('0');
    await page.getByLabel(`第 ${index} 步必填检查结果`, { exact: true }).uncheck();
  }
  await page.getByRole('button', { name: '保存模板', exact: true }).click();
  await expect(page.getByText('模板已本地保存为 v2，只影响后续下发任务。', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '演示工作台', exact: true }).click();
  await start(page);
  await page.getByRole('button', { name: '完成本步', exact: true }).dblclick({ delay: 80 });
  await expect(current(page)).toContainText('检查设备指示灯');
  const saved = await db(page);
  expect(saved.tasks[0].currentStepId).toBe('check-indicators');
  expect(saved.tasks[0].stepRecords.filter(record => record.status === 'completed')).toHaveLength(2);
  expect(saved.events.filter(item => item.type === 'step_completed')).toHaveLength(2);
});
