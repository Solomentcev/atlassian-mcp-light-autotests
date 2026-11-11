# Atlassian MCP Light Autotests

Внешний black-box AQA-проект для проверки [Atlassian MCP Light](https://github.com/Solomentcev/atlassian-mcp-light) через публичный MCP/JSON-RPC контракт поверх STDIO.

AQA не импортирует внутренние классы сервера, не подключает основной проект как Maven dependency и не обращается напрямую к Jira/Confluence REST API. Тестируемый сервер запускается отдельным процессом или новым Docker-контейнером; оба режима используют один MCP Client.

## Технологии

- Java 17 (компиляция через `maven.compiler.release=17`);
- Maven, JUnit 5, AssertJ, Jackson;
- Allure JUnit 5, SLF4J, Logback;
- ProcessBuilder и Docker CLI.

## Документация

| Документ | Назначение |
|---|---|
| [Стратегия тестирования](docs/test-strategy.md) | Объём, уровни, окружение и критерии готовности |
| [Каталог тест-кейсов](docs/test-cases.md) | Сценарии, стабильные ID и статус автоматизации |
| [Техническая спецификация](docs/technical-specification.md) | Архитектура, компоненты, конфигурация и CI |
| [MCP-контракт](docs/mcp-contract.md) | Фактическое поведение тестируемого сервера |
| [Jira-бэклог](docs/jira-backlog.md) | Реализованные и оставшиеся работы |
| [Реестр рисков](docs/risk-register.md) | Риски, меры контроля и статус проверки |

## Архитектура

```text
             JUnit tests
                  |
            McpProcessSession
             /          \
        McpClient    McpServerLauncher
            |              |
            |        ManagedProcess
            |              |
            +-- STDIN/STDOUT --+
                    |
            Atlassian MCP Light
                    |
              Jira / Confluence API
```

- `McpClient` выполняет JSON-RPC обмен по STDIN/STDOUT, сопоставляет request ID и поддерживает classic и stateless запросы.
- `McpProcessSession` объединяет launcher, управляемый процесс, сбор STDERR и lifecycle сессии.
- `ProcessBuilderLauncher` запускает системные команды; `ManagedProcess` управляет процессом и его завершением.
- `LocalMcpServerLauncher` запускает JAR, `DockerMcpServerLauncher` формирует команду запуска нового контейнера.
- `McpServerExtension` создаёт отдельную сессию на тестовый класс. Методы класса выполняются последовательно; независимые классы могут выполняться параллельно.
- `McpStatelessLifecycleIntegrationTest` самостоятельно создаёт отдельную stateless-сессию на каждый тестовый метод.
- STDERR читается отдельно от STDOUT. В STDOUT не должны попадать диагностические сообщения.

## Запуск

### Unit-тесты

Не требуют запущенного Atlassian MCP Light:

```bash
mvn clean test
```

### Компиляция тестовых исходников

Команда проверяет компиляцию, но не запускает тесты:

```bash
mvn -Pintegration -DskipTests test-compile
```

### Интеграционные тесты

Требуется собранный JAR или Docker image тестируемого сервера, а также валидная конфигурация приложения. Jira URL, email и token обязательны для запуска сервера; credentials передаются серверному процессу, а не MCP Client.

Локальный режим:

```bash
AQA_SERVER_MODE=local \
AQA_SERVER_JAR=../atlassian-mcp-light/atlassian-mcp-app/target/atlassian-mcp-light-1.0-SNAPSHOT.jar \
mvn -Pintegration test
```

Docker-режим:

```bash
AQA_SERVER_MODE=docker \
AQA_DOCKER_IMAGE=atlassian-mcp-light:latest \
AQA_DOCKER_ENV_FILE=.runtime.env \
AQA_RUN_ID=${BUILD_TAG:-local} \
mvn -Pintegration test
```

Docker-режим запускает новый контейнер командой `docker run -i`; подключение к уже работающему контейнеру не используется. Реальный запуск контейнера и его аварийный cleanup пока не подтверждены интеграционным прогоном.

### Наборы тестов

```bash
# Smoke: интеграционный запуск и методы с @Tag("smoke")
mvn -Pintegration,smoke clean test

# Regression: unit- и integration-классы с @Tag("regression")
mvn -Pintegration,regression clean test

# Полный набор без дополнительного фильтра по тегам
mvn -Pintegration clean test
```

Интеграционные классы включаются Maven-профилем `integration`, который устанавливает `AQA_RUN_INTEGRATION=true`. Без профиля выполняются unit-тесты.

## Конфигурация

Перед чтением параметров AQA загружает необязательный файл `.env` из текущего рабочего каталога. Приоритет: JVM system properties `aqa.*` → environment variables `AQA_*` → значения из `.env` → defaults. Файл `.env` исключён из Git.

| Параметр | Назначение |
|---|---|
| `AQA_SERVER_MODE` | `local` или `docker`; по умолчанию `local` |
| `AQA_SERVER_JAR` | Путь к исполняемому JAR в local mode |
| `AQA_SERVER_JAVA` | Необязательный Java executable для local mode |
| `AQA_SERVER_WORKING_DIRECTORY` | Необязательный рабочий каталог |
| `AQA_DOCKER_IMAGE` | Image для Docker mode |
| `AQA_DOCKER_ENV_FILE` | Env-файл, передаваемый внутрь контейнера |
| `AQA_DOCKER_EXECUTABLE` | Docker executable; по умолчанию `docker` |
| `AQA_RUN_ID` | Базовый идентификатор запуска; экземплярам добавляется уникальный суффикс |
| `AQA_SERVER_STARTUP_TIMEOUT_SECONDS` | Таймаут готовности MCP; default 15 секунд |
| `AQA_DOCKER_STOP_TIMEOUT_SECONDS` | Таймаут остановки Docker; default 10 секунд |
| `AQA_MCP_PROTOCOL_VERSION` | Версия classic MCP handshake; default `2025-11-25` |

В local mode значения окружения, доступные AQA, передаются серверному Java-процессу. В Docker mode конфигурация самого приложения передаётся через `AQA_DOCKER_ENV_FILE`. Не храните credentials в репозитории, Maven arguments или Allure attachments и не выводите содержимое env-файлов в лог.

## Реализованное покрытие

Стабильный ID тест-кейса связывается с автоматизацией через Allure `@Story` и JUnit tag `case-<ID>`. В каталоге 112 сценариев: 27 связаны с автоматизированными тестами, 85 остаются запланированными. На текущий момент автоматизированы следующие сценарии:

- MCP tools contract: MCP-005–008, MCP-010–013, MCP-022, MCP-023, MCP-028, MCP-034;
- MCP lifecycle: MCP-004, MCP-017, MCP-019–021, MCP-025, MCP-036;
- STDIO/JSON-RPC: MCP-014–016, MCP-029–032;
- Confluence validation: CONF-019.

Отдельные unit-тесты покрывают MCP Client, конфигурацию, session lifecycle, ProcessBuilder, ManagedProcess, построение Docker-команды и маскирование диагностических данных. Они не означают, что соответствующие end-to-end сценарии PROC/Docker/Jenkins уже подтверждены. Подробная матрица находится в [каталоге тест-кейсов](docs/test-cases.md).

## Allure и логи

- JUnit XML: `target/surefire-reports`.
- Allure results: `target/allure-results`.
- AQA log: `target/logs/aqa.log`.
- Локальный просмотр: `mvn allure:serve`.
- Статический отчёт: `mvn allure:report`.

Allure assertions остаются в тестах и используют универсальную обёртку `AllureAssertions`. Allure steps описывают действия, а не дублируют проверки. Диагностические вложения должны содержать только безопасные summaries; секреты маскируются до публикации.

## Ограничения и незавершённые работы

- Jenkins Pipeline в репозитории пока не реализован; документирована целевая последовательность CI.
- Docker launcher проверен unit-тестами формирования команды, но реальный Docker runtime и cleanup контейнера требуют отдельной проверки.
- Полноценные Jira/Confluence CRUD integration tests, TestData registry, write-safety/recovery и preflight пока не реализованы.
- MCP-контракт не содержит delete-инструментов. AQA не должна обходить MCP через прямой Atlassian REST API для удаления тестовых объектов.
- Статус каждого сценария и каждой архитектурной меры фиксируется в каталоге тест-кейсов и реестре рисков.
