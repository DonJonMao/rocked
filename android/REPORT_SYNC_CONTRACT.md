# 巡检报告同步接口约定

手机端 WorkManager 将本地完成报告作为一个 JSON 请求发送到 `inspection.syncUrl`。当前 Android 工程不绑定具体后端框架；后端只要遵守下面的请求和响应语义，就能直接接入。

## 请求

```http
POST /api/inspection/reports
Content-Type: application/json; charset=utf-8
Idempotency-Key: <taskId>
Authorization: Bearer <inspection.syncToken>
```

`Authorization` 在没有 token 时省略。`inspection.syncUrl` 必须是 HTTPS URL。手机端不会跟随 HTTP 重定向；服务端应直接使用最终 HTTPS 地址。手机端 Manifest 同时关闭明文流量（`usesCleartextTraffic=false`），即使配置错误也不会允许应用通过 HTTP 上传。

请求体就是 `InspectionReportCodec.encode(report)` 的结果，当前 `schemaVersion` 为 `1`。照片二进制不在请求体内，照片的 `photoId`、步骤归属、格式、尺寸、大小和 SHA-256 在 `steps[].photos[]` 中保留；照片文件位于手机应用私有目录，后续若后端需要图片上传，应增加独立的受控文件接口。

最小请求示例：

```json
{
  "schemaVersion": 1,
  "taskId": "task-building4-1730800000000",
  "generatedAt": "2026-11-05T10:30:00+08:00",
  "status": "completed",
  "completedStepCount": 2,
  "totalStepCount": 2,
  "flaggedStepCount": 1,
  "template": {
    "id": "chiller",
    "name": "冷水机组巡检",
    "version": 1
  },
  "asset": {
    "id": "chiller-1",
    "code": "B4-CHILLER-1",
    "name": "1#冷机",
    "location": "4号楼地下机房"
  },
  "assignee": { "id": "u1", "name": "张工" },
  "schedule": {
    "date": "2026-11-05",
    "timeSlot": "10:00",
    "shift": "常规班"
  },
  "steps": [
    {
      "step": {
        "id": "pressure",
        "type": "reading",
        "title": "冷凝压力",
        "instruction": "读取仪表并记录",
        "requirements": { "resultRequired": true },
        "fields": []
      },
      "completion": {
        "expectedStepId": "pressure",
        "revision": 1,
        "result": "abnormal",
        "readings": { "value-1": "950" },
        "note": "接近上限，待复核",
        "photoIds": ["photo-0001"]
      },
      "photos": [
        {
          "photoId": "photo-0001",
          "stepId": "pressure",
          "fileName": "photo-0001.jpg",
          "mimeType": "image/jpeg",
          "width": 1280,
          "height": 720,
          "sha256": "<sha256>",
          "sizeBytes": 245678
        }
      ]
    }
  ]
}
```

## 响应和幂等

- `200`、`201` 或其他 `2xx`：报告已按 `Idempotency-Key` 接收，手机端将记录标记为 `succeeded`。
- `408`、`429`、`5xx`：临时失败，手机端记录错误并按退避策略重试。服务端应保证同一个幂等键的重复请求不会生成第二份报告。
- 其他 `4xx`、`3xx`：永久拒绝，手机端标记为 `rejected`，等待配置或数据修正后重新入队。

服务端建议保存以下唯一约束：`tenant_id + idempotency_key`。重复请求可以返回原始成功状态（例如 `200`），无需重新创建记录。响应体可为空；手机端只依据 HTTP 状态码更新队列。

## 联调验收

至少验证：

1. 同一个 `taskId` 连续提交两次只产生一份服务端记录。
2. 断网、超时、`429` 和 `500` 会保留报告并在下一次网络窗口重试。
3. `401/403`、字段校验失败和 `413` 进入 `rejected`，不会无限重试。
4. 服务端不把请求重定向到 HTTP 或其他未经配置的地址。
5. 服务端记录 `taskId`、模板版本、设备码、执行人、生成时间和原始 JSON，便于审计和问题复现。
