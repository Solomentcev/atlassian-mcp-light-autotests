# Спецификация корреляции и надёжности Bridge

Документ дополняет техническую спецификацию AQA и является обязательным контрактом для реализации Bridge и AQA Client. Описанные решения целевые; наличие требований в документации не означает, что компонент уже реализован.

## 1. Цепочка идентификаторов и границы доверия

Для каждого прогона создаётся уникальный `AQA_RUN_ID`. Для каждого тест-кейса используется стабильный `testId`. Для каждой MCP-сессии AQA создаёт UUID `clientSessionId`. Bridge назначает `connectionId`, а после успешного запуска дочернего процесса фиксирует PID.

Обязательная цепочка: `AQA_RUN_ID → testId → clientSessionId → connectionId → PID`.

### Сетевые предпосылки

- AQA и MCP Server подключены к одной выделенной user-defined Docker network.
- К этой сети подключены только AQA и MCP Server; посторонние контейнеры не должны иметь к ней доступ.
- Между AQA и Bridge нет NAT, TCP proxy, service mesh или другого посредника, меняющего source IP/source port. Bridge должен видеть исходный IP:port AQA data socket.
- Data port и control port слушают только внутренний интерфейс контейнерной сети и не публикуются на host.
- Для первой версии процессы внутри AQA-контейнера считаются доверенными. Проверка IP и endpoint не защищает от другого скомпрометированного процесса внутри того же контейнера. Защита от такого противника не входит в модель угроз первой версии.
- Если сетевую топологию с сохранением endpoint гарантировать нельзя, описанный механизм применять нельзя: требуется отдельное изменение архитектуры корреляции до реализации, а не fallback по времени/PID.

## 2. Регистрация соединения без изменения MCP-потока

AQA использует Java TCP socket, который привязывается к локальному адресу и свободному порту до регистрации. Вызов `Socket.bind(localAddress:0)` выполняется до `connect()`; socket остаётся открытым, поэтому порт зарезервирован этим процессом до подключения к Bridge. После регистрации именно этот экземпляр socket подключается к data port. Другой локальный процесс не может занять уже зарезервированный source port.

Последовательность:

1. AQA создаёт `clientSessionId`, привязывает socket к IP интерфейса Docker network и ephemeral source port, не вызывая `connect()`.
2. По control channel отправляет JSONL-команду `REGISTER_SESSION` с `AQA_RUN_ID`, `testId`, `clientSessionId`, заявленным data local IP:port и `registrationTtlMs`.
3. Bridge получает реальный peer IP control connection и проверяет, что заявленный data IP совпадает с ним. Проверяет формат/длину полей, TTL, UUID и допустимость endpoint.
4. Bridge атомарно резервирует endpoint tuple (address family, normalized IP, source port) и `clientSessionId`. Повторная регистрация, конфликт endpoint или уже использованный ID отклоняются.
5. Bridge возвращает `registrationId`, случайный `sessionToken` и `expiresAt`. Token применяется только для последующих control-команд этой сессии; он не передаётся в MCP data stream.
6. AQA подключает тот же заранее привязанный socket к data port. Bridge принимает соединение и сопоставляет фактический remote IP:port с одной незанятой и неистёкшей регистрацией.
7. Сопоставление и перевод регистрации в BOUND выполняются атомарно. Bridge назначает `connectionId`, запускает дочерний процесс и фиксирует PID после успешного старта.
8. AQA запрашивает `GET_SESSION_STATUS` по `clientSessionId` и `sessionToken`, получает `connectionId` и состояние. PID берётся из точной диагностической записи Bridge.
9. После закрытия сессии или истечения TTL endpoint освобождается; использованная регистрация не может быть повторно активирована.

Bridge не запускает дочерний процесс при одной только регистрации: запуск происходит только после успешного сопоставления data connection. Незарегистрированное, просроченное, повторное или неоднозначное соединение закрывается до запуска процесса и фиксируется как `SESSION_REJECTED`.

Data stream остаётся полностью прозрачным: никаких registration token, preamble или служебных полей в MCP TCP-поток не добавляется.

## 3. Контракт control channel

Control channel — отдельный TCP listener и отдельный протокол JSON Lines. Один TCP connection обслуживает ровно один request и один response, после ответа Bridge закрывает control connection; pipelining не используется. Максимальный размер request/response — 16 KiB. Некорректный JSON, превышение размера или времени обработки приводят к безопасному закрытию control connection.

### Формат запроса

Общие поля:
- `requestId` — UUID запроса;
- `command` — команда из allowlist;
- дополнительные поля определяются командой.

Ограничения полей `REGISTER_SESSION`: `runId` — строка 1–128 символов; `testId` — строка 1–256 символов; `clientSessionId` — UUID; `dataLocalAddress` — IP-адрес без hostname; `dataSourcePort` — целое 1–65535; `registrationTtlMs` — целое 1 000–30 000 мс. Bridge не принимает адрес/порт, отличный от реального IP control peer и валидного source port. TTL ограничивается серверной конфигурацией, даже если клиент запросил больше.

Команды:

| Команда | Обязательные поля | Назначение |
|---|---|---|
| `REGISTER_SESSION` | `runId`, `testId`, `clientSessionId`, `dataLocalAddress`, `dataSourcePort`, `registrationTtlMs` | Зарегистрировать заранее привязанный data endpoint |
| `GET_SESSION_STATUS` | `clientSessionId`, `sessionToken` | Получить состояние и connectionId/PID, если уже назначены |
| `GET_SESSION_DIAGNOSTICS` | `clientSessionId`, `sessionToken` | Получить события только указанной сессии |
| `CANCEL_SESSION` | `clientSessionId`, `sessionToken` | Отменить ещё не использованную регистрацию или закрыть незапущенную сессию |

Успешный ответ `REGISTER_SESSION` содержит `requestId`, `ok: true` и `result.registrationId`, `result.sessionToken`, `result.expiresAt`. `registrationId` — созданный Bridge UUID; `sessionToken` — случайное значение не менее 256 бит. Успешный `GET_SESSION_STATUS` возвращает `clientSessionId`, `state`, `connectionId` и `pid`, если они уже назначены. `GET_SESSION_DIAGNOSTICS` возвращает только события указанной сессии. `CANCEL_SESSION` успешен только для состояния REGISTERED; после BOUND отмена регистрации запрещена. Ошибочный ответ содержит `requestId`, `ok: false`, `error.code` и безопасное `error.message`.

Пример регистрации:

```json
{"requestId":"4b9b57f4-12a2-4ad8-97b7-c5c96f1e9d10","command":"REGISTER_SESSION","runId":"run-2026-11-05-001","testId":"PROC-000","clientSessionId":"3e2dfae2-4e25-4e95-b62c-f28f72e55b17","dataLocalAddress":"172.20.0.5","dataSourcePort":41237,"registrationTtlMs":10000}
```

Пример успешного ответа:

```json
{"requestId":"4b9b57f4-12a2-4ad8-97b7-c5c96f1e9d10","ok":true,"result":{"registrationId":"8b6b86ec-5a2c-4d8c-8d31-9c9e2fcb43a7","sessionToken":"<случайный секретный токен>","expiresAt":"2026-11-05T10:00:10Z"}}
```

Пример ошибки:

```json
{"requestId":"4b9b57f4-12a2-4ad8-97b7-c5c96f1e9d10","ok":false,"error":{"code":"ENDPOINT_CONFLICT","message":"Endpoint is already reserved"}}
```

Обязательные коды ошибок: `INVALID_REQUEST`, `UNKNOWN_COMMAND`, `UNAUTHORIZED`, `INVALID_ENDPOINT`, `ENDPOINT_CONFLICT`, `SESSION_CONFLICT`, `REGISTRATION_EXPIRED`, `REGISTRATION_NOT_FOUND`, `SESSION_ALREADY_BOUND`, `REGISTRATION_LIMIT_REACHED`, `SESSION_LIMIT_REACHED`, `RATE_LIMITED`, `MESSAGE_TOO_LARGE`, `INTERNAL_ERROR`.

### Состояния регистрации и сессии

Допустимые состояния: `REGISTERED` → `BOUND` → `STARTING` → `RUNNING` → `STOPPING` → `CLOSED`. Отдельные конечные состояния: `REJECTED`, `START_FAILED`, `STOP_FAILED`, `EXPIRED`, `CANCELLED`.

Переходы состояния и резервирование endpoint должны быть атомарными. Повторная команда не должна повторно создавать процесс или переиспользовать регистрацию. Запрос статуса является read-only и не меняет состояние.

### Защитные ограничения

- Control listener доступен только внутри выделенной Docker network; порт не публикуется на host.
- В сети находятся только AQA и MCP Server. Docker network сама по себе не является ACL для отдельных процессов внутри контейнера.
- Разрешены только перечисленные команды; shell, произвольный запуск процессов, чтение environment и файловые операции отсутствуют.
- Проверяются peer IP, совпадение data IP, UUID, длина/тип полей, допустимый диапазон TTL, уникальность endpoint и одноразовость регистрации.
- `sessionToken` генерируется криптографически стойким генератором, не менее 256 бит, не логируется и не включается в Allure. Он защищает status/diagnostics/cancel команды, но не заменяет проверку endpoint.
- Ограничиваются размер сообщения, число control connections, число незавершённых регистраций, частота запросов и время обработки.
- Control channel не принимает и не возвращает Jira/Confluence credentials или другие секреты.
- При недоступности control channel MCP data protocol не меняется; lifecycle-тест завершается ошибкой корреляции.
- Ошибки не должны раскрывать environment, секреты, локальные пути или stack trace клиенту.

## 4. Правила сопоставления endpoint и обработка гонок

Ключ сопоставления — фактическая пара remote IP:source port, полученная Bridge из принятого data socket, и заранее зарегистрированная пара. Сравнение адресов выполняется в нормализованном бинарном представлении с учётом address family; текстовые варианты одного IPv6/IPv4 адреса не должны давать разные ключи.

- Регистрация создаётся до data connect и имеет конечный TTL.
- Endpoint и clientSessionId резервируются атомарно; две регистрации не могут владеть одним endpoint.
- Data connection может потребить регистрацию только один раз.
- Если data connect пришёл до регистрации, с неверного endpoint, после TTL или с уже использованным endpoint, Bridge закрывает его и не запускает процесс.
- Если data connect не состоялся, AQA отправляет CANCEL_SESSION; при потере control connection регистрация освобождается по TTL.
- Если несколько data connections одновременно претендуют на один endpoint, ровно одна может перевести регистрацию в BOUND; остальные отклоняются.
- При переполнении очереди/лимита сессий Bridge возвращает контролируемую ошибку и не создаёт процесс.
- Нельзя восстанавливать связь по timestamp, порядку подключений, имени потока или PID без connectionId.

## 5. Формат диагностических событий

Формат — JSON Lines, одна JSON-запись на строку. Обязательные поля:
- `timestamp` — UTC ISO-8601;
- `event` — тип события;
- `runId`, `testId`, `clientSessionId`, `registrationId` — после успешной регистрации;
- `controlPeerAddress`, `dataLocalAddress`, `dataSourcePort` — при регистрации;
- `connectionId`, `remoteAddress`, `remotePort` — после binding data connection;
- `pid` — после успешного запуска процесса;
- `reason`, `exitCode` — если применимо.

Для отклонённого незарегистрированного data connection runId/testId/clientSessionId могут отсутствовать; такая запись содержит фактический remote endpoint и причину отказа. Секреты и sessionToken никогда не записываются.

Минимальные события: `SESSION_REGISTERED`, `SESSION_BOUND`, `PROCESS_START_REQUESTED`, `PROCESS_STARTED`, `PROCESS_START_FAILED`, `CLIENT_DISCONNECTED`, `PROCESS_EXITED`, `PROCESS_KILLED`, `SESSION_CLOSED`, `SESSION_REJECTED`, `SESSION_EXPIRED`, `SESSION_CANCELLED`, `SESSION_STOP_FAILED`.

Для каждой регистрации допускается ровно одно терминальное событие из `PROCESS_START_FAILED`, `SESSION_REJECTED`, `SESSION_EXPIRED`, `SESSION_CANCELLED`, `SESSION_CLOSED` или `SESSION_STOP_FAILED`. `PROCESS_EXITED` и `PROCESS_KILLED` являются событиями процесса и не заменяют терминальное событие сессии. События имеют монотонный порядковый номер в пределах сессии. Диагностические сообщения Bridge не попадают в stdout дочернего процесса и MCP data stream. Jenkins архивирует полный журнал; Allure получает только события с точными runId, testId, clientSessionId и connectionId. sessionToken не записывается.

## 6. Параллельность

- Один тестовый класс использует одну MCP-сессию и один дочерний процесс.
- Методы внутри класса выполняются последовательно.
- Параллелятся только классы; каждому классу выделяются отдельные socket, clientSessionId, connectionId и PID.
- `AQA_PARALLELISM` не может превышать `MAX_ACTIVE_SESSIONS` Bridge; проверка выполняется до запуска набора.
- При превышении лимита Bridge отклоняет новую регистрацию/data session контролируемо; клиент не должен зависать.

## 7. Таймауты и завершение

Обязательные конфигурационные параметры и начальные значения первой версии:

| Параметр | Начальное значение | Ограничение/назначение |
|---|---:|---|
| `REGISTRATION_TTL_MS` | 10 000 | Допустимый диапазон 1 000–30 000 |
| `MAX_ACTIVE_SESSIONS` | 2 | Верхняя граница параллельных дочерних процессов |
| `MAX_PENDING_REGISTRATIONS` | 4 | Не более двух ожидающих регистраций на один активный слот |
| `CONTROL_MAX_MESSAGE_BYTES` | 16 384 | Максимальный размер одной control JSONL-записи |
| `CONTROL_MAX_CONNECTIONS` | 8 | Максимум одновременно открытых control connections |
| `CONTROL_RATE_LIMIT_PER_MINUTE` | 60 | Лимит команд на один source IP |
| `CONTROL_CONNECT_TIMEOUT_MS` | 2 000 | Таймаут AQA подключения к control listener |
| `CONTROL_RESPONSE_TIMEOUT_MS` | 2 000 | Таймаут ответа на одну control-команду |
| `DATA_CONNECT_TIMEOUT_MS` | 2 000 | Таймаут AQA подключения к data listener |
| `PROCESS_START_TIMEOUT_MS` | 10 000 | Таймаут запуска дочернего процесса |
| `PROCESS_STOP_TIMEOUT_MS` | 5 000 | Ожидание штатного завершения до force kill |
| `IDLE_SESSION_TIMEOUT_MS` | 60 000 | Таймаут бездействия MCP-сессии |

AQA `AQA_PARALLELISM` по умолчанию равен 2 и не может превышать `MAX_ACTIVE_SESSIONS`. Значения конфигурации валидируются на старте; production-подобные значения могут быть изменены только явной конфигурацией и повторным прогоном reliability-набора. При закрытии data connection Bridge закрывает stdin процесса, ожидает штатного завершения в пределах `PROCESS_STOP_TIMEOUT_MS`, затем принудительно завершает процесс. При остановке Bridge сначала прекращается приём новых control/data connections, затем завершаются активные процессы. Потоки, сокеты и регистрации освобождаются при всех путях завершения.

## 8. Политика повторов и неопределённый результат

Автоматические повторы допустимы только для read-only вызовов, если доказано, что запрос не был отправлен. Запрещён общий retry для `jira_create_issue`, `jira_add_comment`, `jira_transition`, `confluence_create_page` и `confluence_update_page`.

Если write-запрос мог быть отправлен, но ответ потерян, результат фиксируется как `UNKNOWN_OUTCOME`. До разрешённого повтора выполняется сверка по уникальному маркеру или ID. Создание повторяется только после доказательства отсутствия объекта. Комментарии, переходы и обновления не повторяются общим retry-механизмом. Конфликт версии Confluence update приводит к перечитыванию для диагностики и падению сценария, а не к автоматической перезаписи.

## 9. Устойчивый реестр тестовых данных

Реестр — append-only JSONL event log, одна запись на каждое действие записи. До отправки write-запроса AQA синхронно сохраняет `WRITE_INTENT` с `operationId`, runId, testId, инструментом, уникальным marker, временем и безопасными параметрами без секретов. Только после успешного ответа и получения ID сохраняется `WRITE_CONFIRMED` с objectType, objectId/objectKey. При доказанном отказе сохраняется `WRITE_REJECTED`; при потере ответа или аварии до подтверждения остаётся/добавляется `UNKNOWN_OUTCOME`.

Каждая строка дописывается атомарно под общей блокировкой и сбрасывается на диск через flush/force до продолжения операции. На старте восстановления незавершённые WRITE_INTENT без терминального события переводятся в UNKNOWN_OUTCOME и требуют сверки по marker/ID. Реестр не является транзакцией с Atlassian API, поэтому полностью исключить неопределённый исход нельзя; задача журнала — не потерять намерение и обеспечить безопасную последующую сверку.

Jenkins архивирует event log и диагностику при любом исходе Pipeline, если Jenkins agent и workspace остаются доступными. Автоматическое удаление Atlassian-объектов через MCP не выполняется, поскольку соответствующих инструментов в контракте нет.

## 10. Обязательные негативные проверки

- Истёкшая, повторно использованная и неизвестная регистрация.
- Несовпадение IP control клиента с заявленным data IP.
- Неизвестная команда, malformed JSON, превышение размера сообщения и rate limit control channel.
- Конфликт двух регистраций на один endpoint и гонка нескольких data connections.
- Data connection без регистрации и registration TTL expiry.
- Неверный sessionToken для status/diagnostics/cancel и отмена неиспользованной регистрации.
- Параллельные сессии и отсутствие смешения ID, PID и логов.
- Недоступность Bridge, TCP reset, EOF и частичный ответ.
- Ошибка запуска JAR и зависание при завершении процесса.
- Достижение MAX_ACTIVE_SESSIONS.
- SIGTERM Bridge при активных соединениях.
- Потеря ответа после отправки write-запроса и отсутствие автоматического повтора.
- Аварийное завершение AQA/Jenkins и восстановление незавершённого WRITE_INTENT.
- Проверка отсутствия секретов в Jenkins logs, Bridge diagnostics и Allure.
