# Atlassian MCP Light Autotests

Внешний black-box AQA-проект для проверки [Atlassian MCP Light](https://github.com/Solomentcev/atlassian-mcp-light).

AQA взаимодействует с сервером через его штатный MCP/JSON-RPC интерфейс поверх STDIO. Тесты не импортируют внутренние классы основного проекта и не обращаются напрямую к Jira/Confluence REST API. Фактический контракт классического и stateless MCP-режимов, JSON-RPC ошибок и 13 инструментов описан в [mcp-contract.md](docs/mcp-contract.md).

## Технологии

- Java 17
- Maven
- JUnit 5
- AssertJ
- Jackson
- SLF4J + Logback
- Allure Report
- Jenkins
- Docker (для Docker-режима запуска сервера и Jenkins-инфраструктуры)

Для первой версии используется собственный минимальный MCP Client на Java/Jackson. MCP SDK и Mockito не добавляются без отдельной технической необходимости.

## Документация

- [Стратегия и план тестирования](docs/test-strategy.md)
- [Каталог тест-кейсов](docs/test-cases.md)
- [Структура Jira-бэклога](docs/jira-backlog.md)
- [Техническая спецификация AQA-фреймворка](docs/technical-specification.md)
- [Фактический MCP/JSON-RPC контракт сервера](docs/mcp-contract.md)
- [Реестр архитектурных рисков и мер контроля](docs/risk-register.md)

## Архитектура запуска

Jenkins получает два независимых репозитория:

1. atlassian-mcp-light-autotests — исходный код AQA.
2. atlassian-mcp-light — исходный код тестируемого сервера на выбранном commit SHA.

Jenkins собирает тестируемый сервер из исходников. Затем AQA запускает отдельный тестовый экземпляр сервера одним из двух способов:

- **Локальный JAR:** AQA запускает Java-процесс через ProcessBuilder.
- **Docker:** AQA запускает Docker CLI через ProcessBuilder; команда docker run -i запускает отдельный контейнер и передаёт его STDIN/STDOUT через потоки Docker CLI.

В обоих режимах MCP Client работает одинаково: отправляет JSON-RPC сообщения в STDIN процесса и читает ответы из STDOUT. Отличается только способ создания процесса. AQA управляет жизненным циклом запущенного тестового экземпляра: запуск до тестов, штатное завершение после тестов и принудительная очистка при сбое или таймауте.

Основной проект не является Maven-зависимостью AQA. Между проектами сохраняется black-box граница: AQA использует только собранный исполняемый артефакт и публичный STDIO/MCP контракт.

## Структура исходников

Тестовые сценарии и поддерживающая инфраструктура разделены по пакетам:

- `tests/contract`, `tests/transport`, `tests/client`, `tests/session`, `tests/process`, `tests/config`, `tests/support` — только тестовые классы;
- `mcp` — MCP Client и MCP process session;
- `process` — запуск и управление процессами, Docker launcher и сбор STDERR;
- `config` — конфигурация запуска тестового сервера;
- `support/junit`, `support/diagnostics`, `support/fake` — JUnit extension, безопасная диагностика и fake-серверы;
- `testdata` — неизменяемые тестовые данные с доступом через getters, MethodSource providers и эталонный публичный MCP contract.

## Текущая реализация

Базовая инфраструктура STDIO-слоя реализована в `src/test/java`:

- `McpServerLauncher` скрывает способ запуска тестового сервера; `LocalMcpServerLauncher` запускает JAR, а `DockerMcpServerLauncher` создаёт контейнер.
- `ProcessBuilderLauncher` запускает системные команды через `ProcessBuilder`, не объединяя STDOUT и STDERR.
- `ManagedProcess` предоставляет потоки процесса и подтверждает штатное/принудительное завершение.
- `StderrCollector` независимо читает STDERR и хранит ограниченный хвост диагностики.
- `McpClient` отправляет JSON-RPC сообщения по STDIN, читает ответы из STDOUT, проверяет request id и поддерживает классический `initialize → notifications/initialized`.
- `McpProcessSession` объединяет запуск процесса, сбор STDERR, при необходимости classic MCP handshake и последующее завершение процесса; stateless-сессия запускается без initialize.

`McpProcessSession` зависит только от `McpServerLauncher` и использует тот же `ManagedProcess` и `McpClient` для локального и Docker-режимов. Для Docker CLI нужны доступный Docker Engine, собранный image и env-файл; AQA не подключается к уже работающему контейнеру.

### Конфигурация запуска тестов

JUnit-аннотация `@McpServerTest` подключает `McpServerExtension`: для каждого тестового класса создаётся отдельный процесс и MCP-сессия, выполняется readiness handshake, после класса сессия закрывается. Параллельно выполняются разные тестовые классы; методы внутри класса выполняются последовательно и используют его сессию. Настройка находится в `src/test/resources/junit-platform.properties`: фиксированный пул из четырёх потоков. Тесты, которым требуется начальное состояние NEW, например `McpStatelessLifecycleIntegrationTest`, запускают отдельный процесс на каждый метод, чтобы не зависеть от состояния предыдущего сценария. В тестовые методы можно внедрять `McpClient` или `McpProcessSession`.

Режим выбирается переменной `AQA_SERVER_MODE` (или одноимённым system property с префиксом `aqa.`).

Перед чтением системных переменных автоматически загружается файл `.env` из текущего рабочего каталога. Приоритет источников: system properties `aqa.*` → системные environment variables → значения из `.env` → значения по умолчанию. Отсутствующий `.env` не является ошибкой. Поддерживаются строки `KEY=VALUE`, необязательный префикс `export`, одинарные/двойные кавычки и комментарии после пробела; интерполяция и многострочные значения не поддерживаются. Локальный `.env` уже исключён из Git через `.gitignore`. В local mode значения из него передаются запускаемому Java-процессу сервера, поэтому в нём можно хранить и `JIRA_URL`, `JIRA_EMAIL`, `JIRA_TOKEN`; в Docker mode credentials приложения должны находиться в файле, указанном в `AQA_DOCKER_ENV_FILE`. System properties имеют приоритет над environment.

| Переменная | Локальный режим | Docker-режим |
|---|---|---|
| `AQA_SERVER_MODE` | `local` | `docker` |
| `AQA_SERVER_JAR` | Путь к собранному исполняемому JAR | Не используется |
| `AQA_SERVER_JAVA` | Необязательно; по умолчанию Java из текущей JVM | Не используется |
| `AQA_SERVER_WORKING_DIRECTORY` | Необязательно; рабочий каталог процесса | Необязательно; рабочий каталог Docker CLI |
| `AQA_DOCKER_IMAGE` | Не используется | Имя собранного image |
| `AQA_DOCKER_ENV_FILE` | Не используется | Путь к env-файлу для контейнера |
| `AQA_DOCKER_EXECUTABLE` | Не используется | Необязательно; по умолчанию `docker` |
| `AQA_RUN_ID` | Не используется | Базовый ID Jenkins-прогона; к каждому экземпляру сервера добавляется уникальный суффикс |
| `AQA_SERVER_STARTUP_TIMEOUT_SECONDS` | Необязательно; по умолчанию 15 | Необязательно; по умолчанию 15 |
| `AQA_DOCKER_STOP_TIMEOUT_SECONDS` | Не используется | Необязательно; по умолчанию 10 |
| `AQA_MCP_PROTOCOL_VERSION` | Необязательно; по умолчанию `2025-11-25` | Необязательно; по умолчанию `2025-11-25` |

Пример локального запуска:

```bash
AQA_SERVER_MODE=local \
AQA_SERVER_JAR=../atlassian-mcp-light/atlassian-mcp-app/target/atlassian-mcp-light-1.0-SNAPSHOT.jar \
mvn test
```

Пример Docker-режима:

```bash
AQA_SERVER_MODE=docker \
AQA_DOCKER_IMAGE=atlassian-mcp-light:latest \
AQA_DOCKER_ENV_FILE=.runtime.env \
AQA_RUN_ID=${BUILD_TAG:-local} \
mvn test
```

В локальном режиме дочерний процесс сервера наследует environment Jenkins agent и дополнительно получает значения из `.env`. В Docker-режиме переменные тестируемого приложения передаются внутрь контейнера через `AQA_DOCKER_ENV_FILE`; значения из `.env` конфигурируют сам запуск AQA и Docker CLI, но не заменяют env-файл контейнера. Не передавайте секреты через Maven arguments и не выводите содержимое env-файла в лог.

Вся AQA-инфраструктура размещена в `src/test/java`; Jackson также подключён с `test` scope, production-код и отдельный runtime JAR не создаются. Проверяется в том числе ошибка cleanup, если процесс остаётся жив после `destroyForcibly()`. Добавлены Docker launcher, JUnit lifecycle extension и Allure JUnit 5.

Stateless API клиента:
- `discoverStateless(...)` — `server/discover`;
- `listToolsStateless(...)` — `tools/list`;
- `requestStateless(...)` — произвольный stateless request с обязательной metadata в `params._meta`.

Интеграционные проверки публичного контракта находятся в `McpContractIntegrationTest`: discovery, поддерживаемые версии, точные свойства/required/type/ограничения схем всех инструментов из эталонного контракта, annotations, ошибки валидации аргументов и различение MCP tool error и JSON-RPC error. `McpStatelessLifecycleIntegrationTest` отдельно проверяет tools/list до discovery на новом процессе без предварительного classic initialize. `McpTransportContractIntegrationTest` проверяет пустые строки, parse error с продолжением STDIO-цикла, некорректную форму запроса, неизвестный метод, отсутствие ответа на notification и повторный initialize. Интеграционные тесты не запускаются обычной командой `mvn test`.

Для запуска интеграционных проверок требуется собранный JAR или Docker image тестируемого сервера и тестовая конфигурация Jira/Confluence (обязательные `JIRA_URL`, `JIRA_EMAIL`, `JIRA_TOKEN` передаются серверному процессу):

```bash
AQA_SERVER_MODE=local \
AQA_SERVER_JAR=../atlassian-mcp-light/atlassian-mcp-app/target/atlassian-mcp-light-1.0-SNAPSHOT.jar \
mvn clean -Pintegration test
```

Для Docker-режима дополнительно задаются `AQA_DOCKER_IMAGE` и `AQA_DOCKER_ENV_FILE`. Обычная команда `mvn test` выполняет только unit-тесты и не требует запуска Atlassian MCP Light.

Количество выполненных тестов и пропусков определяется по актуальным Surefire/JUnit-отчётам. После текущего рефакторинга необходимо повторно выполнить `mvn clean test` и `mvn -Pintegration test`; результаты предыдущих запусков не подтверждают корректность изменённой структуры. Docker runtime и Jenkins Pipeline пока не проверены.


### Логирование

AQA использует SLF4J API и Logback. Конфигурация находится в
`src/test/resources/logback-test.xml`.

- Консольный вывод направляется в STDERR, не смешивается со STDOUT MCP-процесса.
- Файл `target/logs/aqa.log` предназначен для диагностики и архивирования в Jenkins.
- По умолчанию используется уровень INFO; подробности JSON-RPC доступны на DEBUG через `AQA_LOG_LEVEL=DEBUG`.
- Логируются lifecycle процессов и сессий, имена MCP-методов, request ID, длительность ответов и ошибки.
- Содержимое запросов/ответов, параметры инструментов, credentials и сырой STDERR сервера в лог не записываются.
- STDERR тестируемого процесса отдельно собирается в ограниченный буфер и используется в Allure-диагностике после маскирования.

### Allure-разметка и результаты

Allure JUnit 5 формирует результаты в `target/allure-results`; JUnit XML остаётся в `target/surefire-reports`.

- `@Epic` — крупная область ответственности (MCP Server или AQA Test Infrastructure).
- `@Feature` — проверяемая функция.
- `@Story` — сценарий; для тест-кейсов из `docs/test-cases.md` в названии сохраняется исходный ID.
- `@Severity` — P0 → BLOCKER, P1 → CRITICAL, P2 → NORMAL.
- JUnit `@Tag` — фильтры по типу теста и области; связь с каталогом дополнительно отмечается тегом `case-<ID>`.
- `@DisplayName` и `@Description` содержат статическое название, предусловия, шаги и ожидаемый результат; фактические параметры сценария добавляются через `Allure.parameter`.
- `Allure.step` фиксирует только фактические действия: отправку и получение JSON-RPC сообщений, notifications, raw STDIO line, запуск и завершение процесса. Подготовка параметров и проверки ответов выполняются вне шагов.
- Проверки остаются непосредственно в тестовых методах и используют `AllureAssertions` — универсальную обёртку стандартных AssertJ assertions. Она оформляет каждую проверку отдельным Allure step и показывает описание, actual и expected; доменные assertion-методы в тестах не используются.
- Для JSON-RPC к Allure прикладываются только безопасные summaries: method/request id, поля верхнего уровня, error code, resultType и количество элементов; params и текстовый content не сохраняются. При падении интеграционного теста дополнительно прикладывается STDERR после маскирования Authorization headers и значений переменных TOKEN/PASSWORD/SECRET. Сырые JSON-RPC payload и содержимое ответов Atlassian не прикладываются.

Для локального просмотра используется Allure Maven Plugin 3.1.0 с явно закреплённым runtime Allure Report 2.39.0 (классический интерфейс Allure 2). Сначала выполни тесты, затем запусти:

```bash
mvn allure:serve
```

Команда сформирует отчёт из `target/allure-results`, запустит локальный web-сервер и откроет отчёт в браузере. Для генерации статического отчёта без запуска web-сервера:

```bash
mvn allure:report
```

HTML будет доступен в `target/site/allure-maven/index.html`. В CI Allure results можно публиковать через Jenkins Allure plugin.

## Jenkins Pipeline

1. Checkout AQA-репозитория.
2. Checkout выбранной ревизии основного проекта в отдельный каталог workspace; commit SHA сохраняется в результатах прогона.
3. Сборка основного проекта из исходников в зависимости от выбранного режима запуска.
4. Сборка и запуск AQA-тестов.
5. Публикация JUnit XML и Allure Report.
6. Архивирование диагностических логов и реестра тестовых данных.
7. Cleanup в завершающем блоке Pipeline независимо от результата тестов.

Для локального режима агенту нужны JDK 17 и Maven. Для Docker-режима дополнительно нужны Docker CLI и доступ к Docker Engine. Сам факт запуска Jenkins controller в Docker не означает, что AQA обязана работать в отдельном контейнере.

## Конфигурация и безопасность

- Jira/Confluence credentials хранятся в Jenkins Credentials и передаются только тестируемому серверу через environment.
- Секреты не хранятся в репозитории и не передаются MCP Client без необходимости. В Allure не прикладываются исходные credentials или Authorization headers; STDERR перед вложением проходит маскирование.
- STDOUT процесса зарезервирован под MCP/JSON-RPC. Диагностика читается отдельно из STDERR.
- Не использовать production-проекты, пространства и данные.
- Автоматические повторы неидемпотентных write-операций запрещены. При потере ответа после отправки запроса фиксируется UNKNOWN_OUTCOME.
- В фактическом MCP-контракте нет инструментов удаления Jira-задач и Confluence-страниц. AQA сохраняет реестр созданных объектов для последующей административной очистки, но не удаляет их обходным вызовом Atlassian REST API.

## Статус документации

Документы описывают согласованную целевую архитектуру и требования. Описанная мера не считается реализованной до появления реализации и подтверждающего теста.
