# Jira-бэклог тестирования

Иерархия предназначена для организации тестирования Atlassian MCP Light и разработки AQA-фреймворка.

Уровни:
- Epic — крупная область работ.
- Feature — функциональная часть Epic.
- Story — проверяемая возможность.
- Task — конкретная проверка или техническая задача.

## Текущий статус

Ниже приведён бэклог оставшихся работ. Сводный статус на 12.11.2026:

| Epic | Статус | Что подтверждено |
|---|---|---|
| EPIC 1 — AQA Process Management | Частично реализован | Local launcher, ProcessBuilder, ManagedProcess, MCP session, JUnit extension, конфигурация и параллельный режим существуют. Реальный Docker runtime/аварийный cleanup не проверены |
| EPIC 2 — MCP Protocol Testing | Частично реализован | Автоматизированы contract, stateless lifecycle и STDIO/JSON-RPC кейсы, перечисленные в [каталоге](test-cases.md). Не все сценарии MCP-001…MCP-038 покрыты |
| EPIC 3 — Jira Integration Testing | Запланирован | Реальные Jira CRUD/search/permission integration tests отсутствуют |
| EPIC 4 — Confluence Integration Testing | Запланирован | CONF-019 проверяет validation error; полноценные Confluence CRUD/search integration tests отсутствуют |
| EPIC 5 — Jenkins CI | Не реализован | Jenkinsfile/Pipeline и публикация артефактов не подтверждены |
| EPIC 6 — Reliability and Safe Test Data | Частично реализован | Есть unit-тесты маскирования диагностических данных; registry, recovery и write-safety adapter отсутствуют |

Статус «реализован» присваивается отдельной задаче только при наличии кода и подтверждающего теста. Остальные пункты ниже являются целевыми задачами, а не описанием уже существующей функциональности.

## EPIC 1 — AQA Process Management

### Feature 1.1 — Server launch
**Story:** Запуск тестового экземпляра Atlassian MCP Light.

- Task: получать исходный код основного проекта из отдельного Git checkout.
- Task: фиксировать commit SHA тестируемого сервера.
- Task: собирать основной проект из исходников через Maven Wrapper.
- Task: реализовать запуск JAR через ProcessBuilder.
- Task: реализовать Docker launcher через Docker CLI и docker run -i.
- Task: использовать единый интерфейс launcher для обоих режимов.
- Task: проверять MCP readiness отдельно от факта создания OS-процесса.
- Task: передавать конфигурацию тестируемому серверу через environment.

### Feature 1.2 — STDIO transport
**Story:** Обмен MCP/JSON-RPC сообщениями через стандартные потоки процесса.

- Task: реализовать UTF-8 JSONL writer с newline и flush.
- Task: реализовать чтение JSONL response из STDOUT.
- Task: сопоставлять ответы с request id.
- Task: поддержать notifications без ожидания response.
- Task: обрабатывать EOF, process exit и response timeout.
- Task: читать STDERR независимо от MCP-потока.
- Task: ограничивать объём диагностического буфера и сохранять лог процесса.

### Feature 1.3 — Process lifecycle
**Story:** Надёжное завершение тестового процесса/контейнера.

- Task: реализовать JUnit lifecycle extension для запуска и остановки.
- Task: выполнять graceful shutdown после тестов.
- Task: выполнять force termination после shutdown timeout.
- Task: выполнять cleanup при assertion failure, exception и timeout.
- Task: реализовать Docker cleanup по уникальному container ID/name.
- Task: проверять, что завершение Docker CLI не оставляет контейнер.
- Task: не затрагивать Docker resources других прогонов.

### Feature 1.4 — Parallel execution
**Story:** Изоляция независимых тестовых сессий.

- Task: один процесс на одну MCP-сессию.
- Task: исключить конкурентное чтение одного STDOUT несколькими тестами.
- Task: методы, разделяющие одну сессию, выполнять последовательно.
- Task: параллелить только независимые процессы и тестовые данные.
- Task: изолировать process handles, streams, logs и Docker resources.

## EPIC 2 — MCP Protocol Testing

### Feature 2.1 — Classic lifecycle
- Task: initialize и согласование версии.
- Task: notifications/initialized и переход в READY.
- Task: повторный initialize.
- Task: запросы до READY.
- Task: serverInfo и capabilities.

### Feature 2.2 — Stateless lifecycle
- Task: server/discover.
- Task: stateless tools/list и tools/call.
- Task: проверка обязательных metadata.
- Task: обработка неподдерживаемой protocolVersion.
- Task: проверка независимости stateless и classic lifecycle.

### Feature 2.3 — Tools contract
- Task: проверка 13 имён инструментов.
- Task: проверка descriptions, JSON Schema и annotations.
- Task: успешный вызов read/write инструментов.
- Task: ошибки аргументов и неизвестный инструмент.
- Task: сравнение с mcp-contract.md.

### Feature 2.4 — JSON-RPC validation
- Task: malformed JSON и invalid request.
- Task: неизвестный метод и неверные параметры.
- Task: идентификаторы request/response.
- Task: notification без response.
- Task: пустые строки, trailing tokens и неверный JSON root.
- Task: проверка продолжения работы после негативного запроса.

## EPIC 3 — Jira Integration Testing

- Feature 3.1 — Issue retrieval and search: получение задачи, JQL search, пустая выдача, invalid JQL, права.
- Feature 3.2 — Issue creation: обязательные/необязательные поля, defaults, validation и реестр.
- Feature 3.3 — Issue transition: доступный переход, недоступный переход, неоднозначный исход.
- Feature 3.4 — Comments: добавление комментария, пустое значение, permission denied.
- Feature 3.5 — Projects and users: чтение проекта и пользователя, отсутствующие объекты и права.

Все проверки выполняются через MCP Client. Прямые Jira REST вызовы из AQA не используются.

## EPIC 4 — Confluence Integration Testing

- Feature 4.1 — Pages: получение, создание, обновление, версии и ошибки.
- Feature 4.2 — Spaces: список и получение пространства, пустая выдача, права.
- Feature 4.3 — Search: CQL, пустая выдача, invalid CQL, limit.
- Feature 4.4 — Content validation: заголовок, содержимое, ограничения длины, родительская страница.

Все проверки выполняются через MCP Client. Прямые Confluence REST вызовы из AQA не используются.

## EPIC 5 — Jenkins CI

### Feature 5.1 — Pipeline
- Task: checkout AQA repository.
- Task: checkout основного проекта на выбранной ревизии.
- Task: сохранить commit SHA тестируемого сервера.
- Task: собирать сервер из исходников.
- Task: выбирать локальный JAR или Docker режим.
- Task: запускать Maven tests с выбранными tags.
- Task: публиковать JUnit XML и Allure Report.
- Task: архивировать STDERR/stdout diagnostics и test data registry.
- Task: выполнять cleanup в post/always.

### Feature 5.2 — Agent requirements
- Task: JDK 17 и Maven для локального режима.
- Task: Docker CLI и доступ к Docker Engine для Docker режима.
- Task: не требовать отдельный AQA container.
- Task: обеспечить workspace и доступ к собранному артефакту на агенте.

## EPIC 6 — Reliability and Safe Test Data

### Feature 6.1 — Write safety
- Task: append-only registry для WRITE_INTENT/WRITE_CONFIRMED/WRITE_REJECTED/UNKNOWN_OUTCOME.
- Task: сохранять intent до отправки write-запроса.
- Task: запретить автоматические retry для write-инструментов.
- Task: покрыть политику retry тестовым MCP Client adapter, имитирующим потерю уже полученного ответа.
- Task: разрешать retry read-only запроса только при подтверждённой ошибке до отправки.
- Task: сверять неопределённый результат до повторного создания.
- Task: передавать неоднозначные комментарии/переходы на ручную сверку.

### Feature 6.2 — Secrets and cleanup
- Task: передавать Jira/Confluence credentials через Jenkins Credentials только тестируемому серверу.
- Task: исключить секреты из логов и Allure.
- Task: регистрировать все созданные Jira/Confluence объекты.
- Task: сохранять реестр как Jenkins artifact.
- Task: документировать административную очистку, так как MCP delete tools отсутствуют.
- Task: обеспечить уникальную идентификацию Docker resources каждого AQA run.

## Принципы декомпозиции

1. Не создавать отдельную инфраструктуру, если задачу решает штатный ProcessBuilder/Docker CLI.
2. Не вводить Bridge, TCP proxy, control channel или отдельный контейнер-посредник.
3. Не дублировать внутренние unit-тесты основного приложения.
4. Разделять протокольные проверки и проверки реальных Atlassian API.
5. Не считать техническую задачу выполненной до реализации и тестового подтверждения.
