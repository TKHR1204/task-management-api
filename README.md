# Task API

Java標準ライブラリだけで動く、軽量なタスク管理REST APIです。

Spring Bootや外部DBを使わず、`javac` と `java` だけで起動できます。学習用、API設計のたたき台、小さなフロントエンドの接続先として使いやすい構成です。

## Features

- タスクの作成、取得、更新、削除
- 完了、再オープンの状態変更
- 優先度、期限、タグの管理
- 検索、絞り込み、並び替え
- タスク件数の集計
- CORS / `OPTIONS` 対応

## Requirements

- Java 24 以上
- PowerShell

## Quick Start

```powershell
javac -d out (Get-ChildItem -Recurse src/main/java/*.java)
java -cp out com.example.taskapi.Main
```

起動後、APIは `http://localhost:8080` で利用できます。

```powershell
Invoke-RestMethod http://localhost:8080/health
```

## Configuration

ポート番号は `PORT` 環境変数で変更できます。

```powershell
$env:PORT=9090
java -cp out com.example.taskapi.Main
```

## Data Model

タスク作成・更新で扱えるフィールドです。

```json
{
  "title": "Write LP copy",
  "description": "Draft hero copy and CTA",
  "completed": false,
  "dueDate": "2026-05-12",
  "priority": "HIGH",
  "tags": ["lp", "copy"]
}
```

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | string | Yes | 120文字以内 |
| `description` | string | No | 未指定時は空文字 |
| `completed` | boolean | No | 未指定時は `false` |
| `dueDate` | string | No | `YYYY-MM-DD` 形式 |
| `priority` | string | No | `LOW`, `MEDIUM`, `HIGH`; 未指定時は `MEDIUM` |
| `tags` | string[] | No | 最大10件、各30文字以内 |

## Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/health` | ヘルスチェック |
| `GET` | `/tasks` | タスク一覧 |
| `GET` | `/tasks/stats` | タスク集計 |
| `GET` | `/tasks/{id}` | タスク詳細 |
| `POST` | `/tasks` | タスク作成 |
| `PUT` | `/tasks/{id}` | タスク全体の置き換え |
| `PATCH` | `/tasks/{id}` | タスクの部分更新 |
| `PATCH` | `/tasks/{id}/complete` | 完了にする |
| `PATCH` | `/tasks/{id}/reopen` | 未完了に戻す |
| `DELETE` | `/tasks/{id}` | タスク削除 |

## Query Parameters

`GET /tasks` は以下のクエリパラメータに対応しています。

| Query | Example | Description |
| --- | --- | --- |
| `completed` | `/tasks?completed=false` | 完了状態で絞り込み |
| `q` | `/tasks?q=copy` | タイトル、説明、タグを検索 |
| `priority` | `/tasks?priority=HIGH` | 優先度で絞り込み |
| `tag` | `/tasks?tag=lp` | タグ完全一致で絞り込み |
| `dueBefore` | `/tasks?dueBefore=2026-05-31` | 指定日以前の期限 |
| `dueAfter` | `/tasks?dueAfter=2026-05-01` | 指定日以後の期限 |
| `sort` | `/tasks?sort=dueDate` | 並び替えキー |
| `order` | `/tasks?sort=priority&order=desc` | `asc` または `desc` |

`sort` には `id`, `title`, `dueDate`, `priority`, `createdAt`, `updatedAt` を指定できます。

## Examples

タスクを作成します。

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/tasks `
  -ContentType "application/json" `
  -Body '{"title":"Write LP copy","description":"Draft hero copy and CTA","dueDate":"2026-05-12","priority":"HIGH","tags":["lp","copy"]}'
```

未完了かつ高優先度のタスクを期限順で取得します。

```powershell
Invoke-RestMethod "http://localhost:8080/tasks?completed=false&priority=HIGH&sort=dueDate"
```

一部のフィールドだけ更新します。

```powershell
Invoke-RestMethod -Method Patch http://localhost:8080/tasks/1 `
  -ContentType "application/json" `
  -Body '{"priority":"LOW"}'
```

タスクを完了にします。

```powershell
Invoke-RestMethod -Method Patch http://localhost:8080/tasks/1/complete
```

集計を取得します。

```powershell
Invoke-RestMethod http://localhost:8080/tasks/stats
```

## Project Structure

```text
task-api/
  src/main/java/com/example/taskapi/
    Main.java
    TaskHandler.java
    TaskStore.java
    Task.java
    TaskRequest.java
    TaskPatchRequest.java
    TaskPriority.java
    TaskQuery.java
    TaskStats.java
    Json.java
    ApiException.java
```

## Notes

- データはメモリ上に保存されます。サーバーを停止するとタスクは消えます。
- JSON処理も学習しやすいように自前実装しています。
- 本番用途にする場合は、Spring Boot、永続化DB、認証、テストの追加を検討してください。
