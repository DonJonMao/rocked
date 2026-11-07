import { expect, test, type Page } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import type { ReadingField, Template } from '../../src/domain/types';

const templates = JSON.parse(readFileSync('seed/building4-catalog.json', 'utf8')).templates as Template[];
function goodValue(field: ReadingField): string {
  if (field.expected) return field.expected;
  if (field.kind === 'select') return field.options![0];
  if (field.kind === 'text') return '现场自动运行';
  if (field.min !== undefined && field.max !== undefined) return String((field.min + field.max) / 2);
  if (field.min !== undefined) return String(field.min + 1);
  if (field.max !== undefined) return String(field.max - 1);
  return '10';
}
async function choose(page: Page, template: Template) {
  await page.goto('/');
  await page.getByLabel('巡检模板', { exact: true }).selectOption(template.id);
  await page.getByLabel('目标设备', { exact: true }).selectOption(template.assetIds![0]);
  await page.getByLabel('巡检日期', { exact: true }).fill('2026-11-03');
  await page.getByLabel('巡检时段', { exact: true }).selectOption(template.timeSlots![0]);
  if (template.source?.page === 1) await page.getByLabel('值班工程师', { exact: true }).fill('王工（演示）');
}
async function start(page: Page) {
  await page.getByRole('button', { name: '下发任务', exact: true }).click();
  await page.getByRole('button', { name: '开始巡检', exact: true }).click();
  await page.getByRole('button', { name: '模拟扫描当前设备', exact: true }).click();
  await page.getByRole('checkbox', { name: '我已核对设备名称与位置' }).check();
  await page.getByRole('button', { name: '完成本步', exact: true }).click();
}
async function fill(page: Page, step: Template['steps'][number]) {
  const current = page.getByTestId('current-step');
  await expect(current.getByRole('heading', { name: step.title, exact: true })).toBeVisible();
  await current.getByRole('radio', { name: '正常', exact: true }).check();
  for (const field of step.fields ?? []) {
    const input = current.getByLabel(field.label, { exact: true });
    if (field.kind === 'select') await input.selectOption(goodValue(field));
    else await input.fill(goodValue(field));
  }
}
async function finish(page: Page) {
  await page.getByRole('checkbox', { name: '我已核对以上记录' }).check();
  await page.getByRole('button', { name: '提交巡检记录', exact: true }).click();
  await page.getByRole('button', { name: '查看巡检报告', exact: true }).click();
  await expect(page.getByTestId('inspection-report')).toContainText('未发现异常');
}
for (const template of templates) {
  test(`原表全流程：${template.name}`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await choose(page, template);
    await start(page);
    for (const step of template.steps.slice(1, -1)) {
      await fill(page, step);
      await page.getByRole('button', { name: '完成本步', exact: true }).click();
    }
    await finish(page);
    const report = page.getByTestId('inspection-report');
    await expect(report).toContainText(template.source!.file);
    await expect(report).toContainText('2026-11-03');
    await expect(report).toContainText(template.timeSlots![0]);
    if (template.source?.page === 1) await expect(report).toContainText('王工（演示）');
    await expect(report.locator('.report-step')).toHaveCount(template.steps.length);
    const fields = template.steps.flatMap(step => step.fields ?? []);
    await expect(report.locator('.reading-summary > div')).toHaveCount(fields.length);
    for (const step of template.steps.filter(step => step.fields?.length)) {
      const entry = report.locator('.report-step').filter({ has: page.getByRole('heading', { name: step.title, exact: true }) });
      for (const field of step.fields!) await expect(entry).toContainText(goodValue(field));
    }
    await page.reload();
    await page.getByRole('button', { name: '查看巡检报告', exact: true }).click();
    await expect(page.getByTestId('inspection-report').locator('.reading-summary > div')).toHaveCount(fields.length);
    expect(errors).toEqual([]);
  });
}
for (const viewport of [{ width: 1366, height: 768 }, { width: 1440, height: 900 }, { width: 1920, height: 1080 }]) {
  test(`冷机阈值阻断、留证恢复与报告 ${viewport.width}`, async ({ page }) => {
    await page.setViewportSize(viewport);
    const template = templates.find(t => t.id === 'chiller')!;
    const shot = async (suffix: string) => {
      await mkdir('artifacts/screenshots/building4', { recursive: true });
      await page.evaluate(() => window.scrollTo(0, 0));
      await expect.poll(() => page.locator('img').evaluateAll(images => images.every(image => (image as HTMLImageElement).complete && (image as HTMLImageElement).naturalWidth > 0))).toBe(true);
      await page.screenshot({ path: `artifacts/screenshots/building4/${viewport.width}-${suffix}.png`, fullPage: true });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    };
    await choose(page, template);
    await shot('01-initial');
    await expect(page.getByRole('button', { name: '下发任务', exact: true })).toBeInViewport({ ratio: 1 });
    await start(page);
    for (const step of template.steps.slice(1, 3)) { await fill(page, step); await page.getByRole('button', { name: '完成本步', exact: true }).click(); }
    await expect(page.getByTestId('current-step').getByRole('heading', { name: '冷机 · 冷凝压力', exact: true })).toBeVisible();
    await page.getByRole('radio', { name: '正常', exact: true }).check();
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
    await expect(page.getByTestId('current-step').getByRole('alert')).toContainText('请填写冷凝压力');
    await shot('02-missing-reading');
    await page.getByLabel('冷凝压力', { exact: true }).fill('958');
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
    await expect(page.getByTestId('current-step').getByRole('alert')).toContainText('请登记异常');
    await page.getByRole('radio', { name: '异常', exact: true }).check();
    await page.getByLabel('异常说明（必填）', { exact: true }).fill('冷凝压力达到上限，已记录并待复核');
    await page.getByLabel('故障发现时间（选填）', { exact: true }).fill('09:35');
    await page.getByRole('button', { name: '采集照片', exact: true }).click();
    await page.getByRole('button', { name: '使用演示图片', exact: true }).click();
    await expect(page.getByRole('dialog', { name: '采集巡检照片' })).not.toBeVisible();
    await page.getByLabel('冷凝压力', { exact: true }).focus();
    await shot('03-running');
    await expect(page.getByRole('button', { name: '完成本步', exact: true })).toBeInViewport({ ratio: 1 });
    await page.getByRole('button', { name: '暂停巡检', exact: true }).click();
    await expect(page.getByRole('button', { name: '继续巡检', exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('button', { name: '继续巡检', exact: true })).toBeVisible();
    await shot('04-paused-restored');
    await page.getByRole('button', { name: '继续巡检', exact: true }).click();
    await expect(page.getByLabel('冷凝压力', { exact: true })).toHaveValue('958');
    await expect(page.getByLabel('故障发现时间（选填）', { exact: true })).toHaveValue('09:35');
    await expect(page.getByTestId('current-step').locator('img')).toHaveCount(1);
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
    for (const step of template.steps.slice(4, -1)) { await fill(page, step); await page.getByRole('button', { name: '完成本步', exact: true }).click(); }
    await page.getByRole('checkbox', { name: '我已核对以上记录' }).check();
    await page.getByRole('button', { name: '提交巡检记录', exact: true }).click();
    await page.getByRole('button', { name: '查看巡检报告', exact: true }).click();
    const report = page.getByTestId('inspection-report');
    await expect(report).toContainText('发现异常');
    await expect(report).toContainText('958 kPa');
    await expect(report).toContainText('09:35');
    await shot('05-completed-report');
  });
}

test('关机、待机、不涉及可留原因完成；模板选择与编辑保持独立', async ({ page }) => {
  const template = templates.find(t => t.id === 'chiller')!;
  await choose(page, template);
  await page.getByRole('button', { name: '编辑流程模板' }).click();
  await expect(page.getByLabel('选择流程模板', { exact: true })).toHaveValue('chiller');
  await page.getByLabel('模板名称', { exact: true }).fill('冷机现场巡检');
  await page.getByRole('button', { name: '保存模板', exact: true }).click();
  await expect(page.getByRole('status').last()).toContainText('本地保存');
  await page.getByRole('button', { name: '演示工作台', exact: true }).click();
  await start(page);
  for (const step of template.steps.slice(1, -1)) {
    await expect(page.getByTestId('current-step')).toContainText(step.title);
    const status = step.id.endsWith('01') ? '关机' : step.id.endsWith('02') ? '待机' : '不涉及';
    await page.getByRole('radio', { name: status, exact: true }).check();
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
    await expect(page.getByTestId('current-step').getByRole('alert')).toContainText('原因');
    await page.getByLabel('状态原因（必填）', { exact: true }).fill('备用机未运行，本项不适用');
    await page.getByRole('button', { name: '完成本步', exact: true }).click();
  }
  await finish(page);
  await expect(page.getByTestId('inspection-report')).toContainText('冷机现场巡检');
  await expect(page.getByTestId('inspection-report')).toContainText('不涉及');
});
