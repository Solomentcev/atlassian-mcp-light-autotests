# Jira-бэклог тестирования

Иерархия предназначена для организации тестирования Atlassian MCP Light, а не для планирования разработки самого AQA-фреймворка.

Уровни:
- Epic — крупная область тестирования.
- Feature — функциональная часть Epic.
- Story — проверяемая пользовательская/системная возможность.
- Task — конкретная проверка или группа близких проверок.

## EPIC 1 — MCP Protocol Testing

Протокольные сценарии выполняются через MCP Client поверх TCP Bridge. Bridge не интерпретирует MCP/JSON-RPC сообщения.

### Feature 1.1 — Initialization
**Story:** Проверка инициализации MCP-сессии.
- Task: успешный `initialize`.
- Task: проверка согласования поддерживаемых версий.
- Task: проверка fallback на `2025-11-25` для неизвестной непустой classic protocolVersion.
- Task: проверка информации о сервере.
- Task: отправка `notifications/initialized`.
- Task: повторный `initialize` после READY отклоняется с `-32600` без повреждения текущей сессии.
- Task: `tools/list` в состоянии INITIALIZATION_PENDING возвращает `-32001`.

### Feature 1.2 — Tools Discovery
**Story:** Получение и проверка списка MCP-инструментов.
- Task: вызов `tools/list`.
- Task: наличие всех 13 инструментов.
- Task: проверка названий и описаний.
- Task: проверка JSON Schema параметров и annotations (readOnlyHint, destructiveHint, idempotentHint, openWorldHint).

### Feature 1.3 — Tool Execution
**Story:** Вызов MCP-инструментов.
- Task: успешный `tools/call`.
- Task: передача аргументов.
- Task: проверка структуры результата.
- Task: обработка ошибки инструмента.

### Feature 1.4 — Stateless MCP Mode
**Story:** Проверка stateless MCP-взаимодействия без классической инициализации.
- Task: вызов `server/discover` с версией `2026-07-28`.
- Task: проверка supportedVersions, capabilities, ttlMs, cacheScope и serverInfo metadata.
- Task: stateless `tools/list` без initialize.
- Task: stateless `tools/call` без initialize.
- Task: проверка обязательных ключей `_meta`.
- Task: обработка неподдерживаемой protocolVersion (`-32022`).
- Task: проверка, что stateless-запросы не меняют классическое состояние lifecycle.

### Feature 1.5 — JSON-RPC Validation
**Story:** Обработка некорректных JSON-RPC сообщений.
- Task: некорректный JSON.
- Task: некорректная структура запроса.
- Task: неизвестный метод.
- Task: отсутствующие обязательные параметры.
- Task: некорректные типы параметров.

## EPIC 2 — Jira Integration Testing

### Feature 2.1 — Jira Issue Retrieval
**Story:** Получение задачи Jira.
- Task: получение существующей задачи.
- Task: получение задачи с необязательными полями.
- Task: обработка несуществующей задачи.
- Task: обработка отказа в доступе.

### Feature 2.2 — Jira Issue Search
**Story:** Поиск задач Jira.
- Task: поиск по корректному JQL.
- Task: поиск без результатов.
- Task: ограничение количества результатов.
- Task: проверка значения `limit` по умолчанию и допустимого диапазона; контракт не предоставляет offset/cursor для перехода на следующую страницу.
- Task: обработка некорректного JQL.

### Feature 2.3 — Jira Issue Creation
**Story:** Создание задачи Jira.
- Task: создание с обязательными полями.
- Task: создание с дополнительными полями.
- Task: проверка отсутствующих обязательных параметров.
- Task: обработка некорректных значений.

### Feature 2.4 — Jira Issue Transition
**Story:** Изменение статуса задачи Jira.
- Task: успешный переход.
- Task: выбор перехода по частичному совпадению названия.
- Task: обработка недоступного статуса.
- Task: обработка отсутствующего перехода.

### Feature 2.5 — Jira Issue Comments
**Story:** Добавление комментария к задаче.
- Task: успешное добавление комментария по MCP-ответу `jira_add_comment`.
- Task: проверка пустого комментария; независимое чтение комментария через MCP не входит в проверку, поскольку соответствующего инструмента нет.
- Task: обработка ошибки доступа.

### Feature 2.6 — Jira Project Retrieval
**Story:** Получение проекта Jira.
- Task: получение существующего проекта.
- Task: обработка несуществующего проекта.

### Feature 2.7 — Jira User Retrieval
**Story:** Получение пользователя Jira.
- Task: получение пользователя по идентификатору.
- Task: обработка отсутствующих полей пользователя.
- Task: обработка несуществующего пользователя.

## EPIC 3 — Confluence Integration Testing

### Feature 3.1 — Confluence Page Retrieval
**Story:** Получение страницы Confluence.
- Task: получение существующей страницы.
- Task: проверка заголовка и содержимого.
- Task: обработка несуществующей страницы.
- Task: проверка ограничения длины содержимого.

### Feature 3.2 — Confluence Space Management
**Story:** Получение информации о пространствах Confluence.
- Task: получение существующего пространства.
- Task: получение списка пространств.
- Task: обработка пустого списка.
- Task: проверка ограничения количества элементов параметром `limit`; переход к следующей странице контрактом не предусмотрен.

### Feature 3.3 — Confluence Content Search
**Story:** Поиск страниц Confluence.
- Task: поиск по корректному CQL.
- Task: поиск без результатов.
- Task: проверка ограничения количества результатов параметром `limit`; переход к следующей странице контрактом не предусмотрен.
- Task: обработка некорректного CQL.

### Feature 3.4 — Confluence Page Creation
**Story:** Создание страницы Confluence.
- Task: создание с обязательными полями.
- Task: создание с родительской страницей.
- Task: проверка отсутствующих обязательных параметров.
- Task: обработка некорректных значений.

### Feature 3.5 — Confluence Page Update
**Story:** Обновление страницы Confluence.
- Task: обновление содержимого.
- Task: обновление заголовка.
- Task: обновление заголовка и содержимого.
- Task: проверка увеличения версии.
- Task: обработка некорректной версии.

## EPIC 4 — STDIO Bridge and Session Lifecycle Testing

### Feature 4.1 — Bridge Availability
**Story:** Проверка доступности TCP Bridge во внутренней Docker network.
- Task: успешное TCP-подключение к Bridge.
- Task: контролируемая ошибка при недоступности Bridge.
- Task: проверка, что порт Bridge не опубликован на хост.

### Feature 4.2 — Child Process Lifecycle
**Story:** Проверка управления дочерними MCP-процессами.
- Task: создание отдельного процесса на каждое TCP-соединение.
- Task: завершение процесса при штатном закрытии соединения.
- Task: завершение процесса при аварийном отключении клиента.
- Task: обработка ошибки запуска JAR.
- Task: соблюдение лимита активных сессий и таймаутов.
- Task: структурированные JSONL диагностические события Bridge с timestamp, runId, testId, clientSessionId, connectionId, endpoint, PID, причиной и результатом завершения.
- Task: отдельный control channel с JSONL-контрактом REGISTER_SESSION/GET_SESSION_STATUS/GET_SESSION_DIAGNOSTICS/CANCEL_SESSION.
- Task: заранее привязанный AQA socket endpoint и сопоставление remote IP:port без NAT/proxy.
- Task: атомарное резервирование IP:port endpoint, проверка фактического source port, TTL, одноразовость, sessionToken и конечные состояния регистрации.
- Task: идемпотентный повтор REGISTER_SESSION после потери ответа по requestId/clientSessionId без продления TTL.
- Task: отклонение конфликтующего повторного clientSessionId с другим requestId или endpoint.
- Task: валидация размера сообщений, allowlist команд, rate limit, числа незавершённых регистраций и недоступности порта с host.
- Task: проверка sessionToken для status/diagnostics/cancel и освобождение endpoint по CANCEL_SESSION.
- Task: проверка отсутствия canary secrets в Jenkins logs, Bridge diagnostics и Allure attachments.
- Task: проверка доверенной сетевой границы: в выделенной Docker network находятся только AQA и MCP Server; процессы внутри AQA container являются доверенными.
- Task: проверка детерминированной корреляции событий с тестовыми сессиями, включая параллельные сессии.
- Task: отклонение отсутствующей/неоднозначной корреляции и сохранение логов в Jenkins.
- Task: обработка ошибок запуска/остановки, TCP reset, лимита сессий и graceful shutdown.
- Task: отдельный `bridge-test-fixture.jar` с режимами ECHO, IGNORE_STDIN_EOF и PARTIAL_RESPONSE; fixture stderr markers должны коррелироваться с connectionId.
- Task: проверка отказа запуска при существующем некорректном JAR через `MCP_SERVER_JAR_PATH`.
- Task: отправка SIGTERM Bridge со стороны Jenkins/Compose orchestrator, а не AQA container.

### Feature 4.3 — Transparent STDIO Proxy
**Story:** Проверка прозрачной передачи STDIO через Bridge.
- Task: передача TCP → stdin дочернего процесса.
- Task: передача stdout дочернего процесса → TCP.
- Task: отдельная маршрутизация stderr в логи контейнера.
- Task: сохранение UTF-8 и исходного framing.
- Task: отсутствие диагностических сообщений в MCP-потоке.
- Task: передача нескольких последовательных сообщений в рамках одной сессии.

### Feature 4.4 — Session Isolation
**Story:** Проверка изоляции параллельных MCP-сессий.
- Task: создание двух независимых соединений.
- Task: отсутствие смешения запросов и ответов.
- Task: независимое закрытие сессий.
- Task: проверка отсутствия зависших дочерних процессов.

## EPIC 5 — Reliability and Safe Execution

### Feature 5.1 — Retry and Idempotency
**Story:** Предотвращение дублирующих и небезопасных операций.
- Task: разрешить ограниченные retry только для read-only операций при ошибке до отправки.
- Task: запретить общий retry для Jira/Confluence write-инструментов.
- Task: фиксировать UNKNOWN_OUTCOME при потере ответа после отправки write-запроса.
- Task: сверять результат по уникальному маркеру/ID до разрешения повторного create.
- Task: проверять конфликт версии Confluence update без автоматической перезаписи.

### Feature 5.2 — Parallel Isolation
**Story:** Изоляция параллельных тестов.
- Task: одна отдельная MCP-сессия на тестовый класс.
- Task: последовательное выполнение методов внутри класса.
- Task: ограничение JUnit workers значением MAX_ACTIVE_SESSIONS Bridge.
- Task: проверка отсутствия смешения тестовых данных, ответов и diagnostics.

### Feature 5.3 — Durable Test Data Registry
**Story:** Сохранение реестра тестовых данных при любом исходе.
- Task: append-only JSONL event log с WRITE_INTENT до отправки write-запроса.
- Task: WRITE_CONFIRMED/WRITE_REJECTED и восстановление незавершённого intent как UNKNOWN_OUTCOME.
- Task: безопасная конкурентная запись, атомарная строка и flush/force.
- Task: отдельная сверка неопределённых результатов write-операций; запрет retry для jira_transition также обязателен.
- Task: архивирование реестров и логов в Jenkins post/always при доступных agent/workspace/Docker daemon.
- Task: документирование и проверка host-level очистки orphan-контейнеров при потере Jenkins agent/host.
- Task: проверка безопасного удаления только ресурсов с метками AQA_RUN_ID и истёкшим TTL; немаркированные ресурсы не затрагиваются.
- Task: маркировка всех Docker containers/networks/volumes тремя labels: `managed-by`, `aqa-run-id`, `expires-at` (UTC Unix epoch seconds); janitor запускается ежечасно, TTL 24 часа, Pipeline timeout не более 12 часов.

## EPIC 6 — Test Data Management

В фактическом MCP-контракте нет инструментов удаления Jira-задач и Confluence-страниц. Первая версия AQA регистрирует созданные объекты и сохраняет реестр для последующей ручной/административной очистки; удаление не выполняется через MCP и не подменяется прямыми вызовами Atlassian API в тестах.

### Feature 6.1 — Jira Test Data
**Story:** Подготовка и управление тестовыми данными Jira.
- Task: проверка готовности тестового Jira/Confluence окружения до запуска integration-набора.
- Task: выделенный тестовый проект.
- Task: создание тестовых задач.
- Task: уникальная маркировка тестовых объектов.
- Task: регистрация ID и маркеров созданных задач; подготовка реестра для последующей административной очистки. В первой версии MCP-инструмент удаления отсутствует.

### Feature 6.2 — Confluence Test Data
**Story:** Подготовка и управление тестовыми данными Confluence.
- Task: выделенное тестовое пространство.
- Task: создание тестовых страниц.
- Task: уникальная маркировка тестовых объектов.
- Task: регистрация ID и маркеров созданных страниц; подготовка реестра для последующей административной очистки. В первой версии MCP-инструмент удаления отсутствует.

## Принципы декомпозиции

1. Не создавать отдельный Task для каждой комбинации входных значений, если они относятся к одному сценарию и могут быть параметризованы.
2. Не дублировать внутренние unit-проверки основного проекта без необходимости.
3. Разделять проверки протокола и проверки реальных Jira/Confluence API.
4. Для операций создания и изменения предусматривать проверку результата чтением созданного/изменённого объекта.
5. Негативные сценарии включать в объём Story, но не все автоматически относить к критическому набору.
