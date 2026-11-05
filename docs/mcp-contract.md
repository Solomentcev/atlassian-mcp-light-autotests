# Фактический MCP/JSON-RPC контракт Atlassian MCP Light

## Статус и источник

Контракт сверён с исходным кодом основного репозитория `Solomentcev/atlassian-mcp-light`, ветка `master`, commit `4b010d0c1d7e2b53e26d1f5fb15a0151825889fd` (3 ноября 2026).

Проверенные файлы:
- `atlassian-mcp-app/src/main/java/ru/solomentsev/app/AtlassianMcpServer.java`
- `atlassian-core/src/main/java/ru/solomentsev/core/protocol/McpProtocol.java`
- `atlassian-core/src/main/java/ru/solomentsev/core/protocol/ToolSchema.java`
- `atlassian-jira/src/main/java/ru/solomentsev/jira/tools/JiraToolProvider.java`
- `atlassian-jira/src/main/java/ru/solomentsev/jira/service/JiraService.java`
- `atlassian-confluence/src/main/java/ru/solomentsev/confluence/tools/ConfluenceToolProvider.java`
- `atlassian-confluence/src/main/java/ru/solomentsev/confluence/service/ConfluenceService.java`
- `atlassian-core/src/main/java/ru/solomentsev/core/config/ConfigLoader.java`
- `atlassian-core/src/main/java/ru/solomentsev/core/config/AppConfig.java`

Это описание реализации на момент проверки, а не обещание соответствия любой будущей версии MCP. При обновлении основного проекта контракт необходимо сверять повторно. В AQA-прогоне следует фиксировать commit SHA основного проекта.

## 1. Транспорт STDIO

- Кодировка — UTF-8.
- Каждое входящее сообщение — отдельная строка JSON (newline-delimited JSON).
- Сервер читает stdin построчно через `BufferedReader.readLine()`.
- Пустые/состоящие из пробелов строки пропускаются без ответа.
- Для каждого ответа сериализуется один JSON-объект, затем записывается `\n` и выполняется flush.
- Входная строка должна содержать один JSON-документ; trailing tokens запрещены.
- Корневое значение запроса должно быть JSON-объектом.
- Уведомления не получают ответ.
- Логи приложения направляются в stderr; stdout зарезервирован под JSON-RPC.

Bridge в целевой AQA-архитектуре должен передавать байты между TCP и stdin/stdout дочернего процесса без разбора или изменения JSON-RPC. stderr должен оставаться отдельным диагностическим потоком.

## 2. Общие правила JSON-RPC

Запрос должен содержать:
- `jsonrpc: "2.0"`;
- непустой строковый `method`;
- `params` в виде JSON-объекта, если поле присутствует;
- для request — `id` строкового или целочисленного типа.

Запрос без id трактуется как notification. Для методов-запросов (`initialize`, `server/discover`, `tools/list`, `tools/call`, `ping`) отсутствие id приводит к `-32600`. Для обычного уведомления ответ не формируется.

Транспортные и протокольные ошибки:
| Ситуация | Код / поведение |
|---|---|
| Некорректный JSON | `-32700 Parse error`, id = null |
| Корень JSON не объект | `-32600 Invalid Request`, id = null |
| Некорректная структура JSON-RPC или id | `-32600 Invalid Request` |
| Неизвестный метод | `-32601 Method not found` |
| Неверные параметры метода / metadata | `-32602 Invalid params` |
| Запрос классического режима до READY | `-32001 Server not initialized` |
| Неподдерживаемая stateless protocolVersion | `-32022 Unsupported protocol version`, в data присутствует supportedVersions |

После строки с некорректным JSON транспорт продолжает читать следующие строки.

## 3. Классический MCP lifecycle

Классический режим используется для handshake-версий:
- `2024-11-05`;
- `2025-11-25`.

Последовательность:
1. Отправить `initialize` с `params.protocolVersion`, `params.capabilities` (объект) и `params.clientInfo` (объект с непустыми строковыми `name` и `version`).
2. Получить ответ с `result.protocolVersion`, `result.capabilities.tools` и `result.serverInfo`.
3. Отправить notification `notifications/initialized` без id.
4. После этого доступны `tools/list` и `tools/call`.

Сервер выбирает запрошенную версию, если она равна `2024-11-05` или `2025-11-25`. Для любой другой непустой строки сервер отвечает версией `2025-11-25`; неизвестная версия сама по себе не приводит к JSON-RPC error.

Состояния процесса: NEW → INITIALIZATION_PENDING → READY. Повторный `initialize` после первого принятого initialize возвращает `-32600 Invalid Request: initialize has already been processed`. До READY методы `tools/list` и `tools/call` возвращают `-32001`. `ping` в классическом режиме возвращает пустой объект result и не требует READY.

`notifications/initialized` переводит процесс в READY только из INITIALIZATION_PENDING. На notification сервер не отправляет JSON-RPC response.

## 4. Stateless MCP режим

Stateless-режим реализован отдельно и не использует состояние классического lifecycle. Для каждого запроса клиент передаёт metadata в `params._meta`.

Точные ключи:
- `io.modelcontextprotocol/protocolVersion` — непустая строка;
- `io.modelcontextprotocol/clientCapabilities` — объект;
- `io.modelcontextprotocol/clientInfo` — необязательный объект с непустыми строковыми `name` и `version`.

Поддерживаемая stateless-версия: `2026-07-28`. Если версия отличается, сервер возвращает `-32022 Unsupported protocol version`; data содержит `supportedVersions: ["2026-07-28"]`.

Запрос считается современным, если:
- method = `server/discover`; или
- `params._meta` содержит хотя бы один из ключей protocolVersion, clientCapabilities или clientInfo.

При распознанном современном запросе отсутствие обязательной metadata или неверный тип приводит к `-32602`. В `_meta` допускаются и дополнительные поля, если они не запрещены обработчиком конкретного метода.

Поддерживаемые stateless-методы:
- `server/discover`;
- `tools/list`;
- `tools/call`.

Для `server/discover` и `tools/list` разрешённые поля params — только `_meta`. Для `tools/call` разрешены `name`, `arguments`, `_meta`.

### server/discover result

Результат содержит:
- `resultType: "complete"`;
- `supportedVersions: ["2026-07-28", "2025-11-25", "2024-11-05"]`;
- `capabilities.tools: {}`;
- `_meta.io.modelcontextprotocol/serverInfo` с `name: "atlassian-lite"` и `version: "1.0.0"`;
- `ttlMs: 300000`;
- `cacheScope: "public"`.

Успешные ответы stateless-методов `tools/list` и `tools/call` также получают `resultType: "complete"` и `_meta.io.modelcontextprotocol/serverInfo`.

Stateless-запросы не переводят классический lifecycle в READY и не требуют предварительного `initialize`.

## 5. tools/list

Успешный результат имеет поле `result.tools` — массив определений. Для каждого инструмента сервер публикует:
- `name`;
- `description`;
- `inputSchema`;
- `annotations`, если они непустые.

Все 13 инструментов должны быть проверены через фактический ответ `tools/list`. Список имён и схема ниже — ожидаемый контракт, извлечённый из providers; фактический ответ остаётся источником истины для теста.

Общие правила `ToolSchema`:
- корневой тип схемы — object;
- `additionalProperties: false`;
- строковые поля имеют `minLength: 1`;
- обязательные поля перечислены в `required`;
- поддерживаются типы string и integer;
- integer может иметь minimum/maximum;
- серверная валидация отклоняет отсутствующие обязательные поля, null, пустые/пробельные строки, неверный тип, неизвестные аргументы и значения за границами integer.

## 6. Каталог 13 инструментов

### Jira

| Инструмент | Обязательные параметры | Необязательные параметры | Ограничения / значения по умолчанию |
|---|---|---|---|
| `jira_get_issue` | `issue_key: string` | — | Ключ задачи |
| `jira_search` | `jql: string` | `limit: integer` | limit 1–100, default 5 |
| `jira_create_issue` | `project_key: string`, `summary: string`, `description: string` | `issue_type: string` | issue_type default `Bug` |
| `jira_transition` | `issue_key: string`, `transition_name: string` | — | Название перехода/статуса |
| `jira_add_comment` | `issue_key: string`, `comment: string` | — | Текст комментария |
| `jira_get_project` | `project_key: string` | — | Ключ проекта |
| `jira_get_user` | `user_identifier: string` | — | Ключ или имя пользователя |

### Confluence

| Инструмент | Обязательные параметры | Необязательные параметры | Ограничения / значения по умолчанию |
|---|---|---|---|
| `confluence_get_page` | `page_id: string` | — | ID страницы |
| `confluence_list_spaces` | — | `limit: integer` | limit 1–100, default 25 |
| `confluence_get_space` | `space_key: string` | — | Ключ пространства |
| `confluence_search` | `cql: string` | `limit: integer` | limit 1–100, default 5 |
| `confluence_create_page` | `space_key: string`, `title: string`, `content: string` | `parent_id: string` | content в формате Confluence Storage |
| `confluence_update_page` | `page_id: string` | `version: integer`, `title: string`, `content: string` | version минимум 1; требуется хотя бы title или content |

Для `confluence_update_page` условие «title или content» проверяется обработчиком инструмента, а не выражается в JSON Schema `required`. Передача только `page_id` (или `page_id` вместе только с `version`) приводит к ошибке инструмента.

### 6.1. Ограничение выдачи и пагинация

В схемах `jira_search`, `confluence_search` и `confluence_list_spaces` опубликован только параметр `limit`. MCP-клиент не может передать `offset`, `cursor`, `startAt`, `nextPageToken` или запросить конкретную страницу.

Внутренняя реализация сервисов сама обходит страницы Atlassian API, пока не наберёт `limit` результатов или API не вернёт пустую страницу/отсутствие следующего токена: Jira использует `nextPageToken`, Confluence — `_links.next` и локально формируемый `start`. Это внутренняя деталь сервера, а не публичный MCP-параметр.

AQA проверяет диапазон и default `limit`, а также то, что возвращаемое число элементов не превышает лимит. Отдельное управление страницами со стороны MCP-клиента контрактом не предусмотрено; тесты не должны ожидать или передавать токены продолжения.

## 7. tools/call и формат ошибок

В `params` вызова передаются:
- `name` — непустая строка;
- `arguments` — JSON-объект; если отсутствует, сервер подставляет пустой объект;
- `_meta` — только для stateless-вызова.

Успешный вызов возвращает JSON-RPC result с:
`content: [{ "type": "text", "text": "<строковый результат инструмента>" }]`.

Если обработчик, валидация аргументов или поиск инструмента выбрасывает исключение, сервер всё равно возвращает JSON-RPC result, содержащий:
- `content` с текстовым сообщением, начинающимся с `Ошибка: `;
- `isError: true`.

Это MCP tool error, а не JSON-RPC error. Транспортные и протокольные ошибки используют объект верхнего уровня `error` с кодом и сообщением. В тестах эти два вида ошибок необходимо различать.

## 8. Annotations

Providers добавляют аннотации:
- Jira read-only: `readOnlyHint=true`, `destructiveHint=false`, `idempotentHint=true`;
- Jira create/transition/comment: `readOnlyHint=false`; transition имеет `destructiveHint=true`; операции записи неидемпотентны;
- Confluence read-only инструменты: `readOnlyHint=true`, `destructiveHint=false`, `idempotentHint=true`;
- Confluence create: `readOnlyHint=false`, `destructiveHint=false`, `idempotentHint=false`;
- Confluence update: `readOnlyHint=false`, `destructiveHint=true`, `idempotentHint=false`;
- во всех этих аннотациях `openWorldHint=true`.

## 9. Конфигурация процесса

Обязательные параметры:
- `JIRA_URL`;
- `JIRA_EMAIL`;
- `JIRA_TOKEN`.

Confluence URL необязателен: при отсутствии используется `JIRA_URL/wiki`.

Поддерживаемые параметры:
- `MCP_MAX_TEXT_LENGTH`: положительное целое, максимум 1 000 000, default 4000;
- `MCP_MAX_RESPONSE_BYTES`: положительное целое, максимум 104 857 600, default 2 097 152.

Приоритет источников конфигурации: JVM properties → environment → `.env` → `application.properties`. Имена JVM properties: `jira.url`, `jira.email`, `jira.token`, `confluence.url`, `mcp.max-text-length`, `mcp.max-response-bytes`. Для файлов поддерживаются пути через `ATLASSIAN_ENV_FILE` / `ATLASSIAN_PROPERTIES_FILE` или JVM properties `atlassian.env.file` / `atlassian.properties.file`.

Ошибки обязательной конфигурации приводят к завершению процесса с exit code 1 до запуска STDIO-цикла. URL Jira и явно заданный URL Confluence должны быть абсолютными HTTPS URL без user-info, query и fragment.

## 10. Обязательные группы AQA-проверок

1. STDIO framing, UTF-8, пустые строки, parse error и продолжение обработки.
2. JSON-RPC validation, request IDs, неизвестные методы и notification semantics.
3. Классический lifecycle: initialize, согласование версии, notifications/initialized, повторный initialize, запросы до READY.
4. Stateless lifecycle: discovery, metadata validation, версия, stateless tools/list и tools/call без initialize.
5. tools/list: 13 уникальных имён, описания, inputSchema, required, additionalProperties, типы, границы и annotations.
6. tools/call: успех, неизвестный инструмент, отсутствующие/null/лишние аргументы, неверные типы и значения за границами; различение JSON-RPC error и isError.
7. Конфигурация и запуск процесса.
8. Интеграция с реальными тестовыми Jira и Confluence ресурсами.

## 11. Ограничения интерпретации

- Наличие stateless-обработчика в исходниках не означает, что его совместимость с внешней спецификацией MCP уже подтверждена независимой сертификацией. AQA проверяет фактическое поведение этого приложения.
- Описание Bridge в других документах — целевая архитектура AQA; Bridge не является частью проверенных исходников основного MCP-приложения.
- Конкретные тексты ответов Jira/Confluence сервисов зависят от API и тестовых данных; контракт инструмента гарантирует MCP-обёртку, а не неизменный текст бизнес-ответа.
