# Atlassian MCP Light Autotests

Внешний black-box AQA-проект для проверки [Atlassian MCP Light](https://github.com/Solomentcev/atlassian-mcp-light).

Тесты проверяют Atlassian MCP Light как black-box приложение. Фактический контракт классического и stateless MCP-режимов, JSON-RPC ошибок и всех 13 инструментов зафиксирован в [mcp-contract.md](docs/mcp-contract.md) по исходному коду основного проекта. AQA Client подключается по TCP к STDIO Bridge в отдельном Docker-контейнере; Bridge запускает дочерний процесс MCP-сервера и прозрачно проксирует stdin/stdout. Внутренние Java-классы основного приложения не являются объектом тестирования.

## Технологии

- Java 17
- Maven
- JUnit 5
- AssertJ
- Jackson
- Allure Report
- Jenkins

Для первой версии используется собственный минимальный MCP Client на Java/Jackson, работающий поверх TCP-соединения с Bridge. MCP SDK и Mockito не включаются без отдельной технической необходимости.

## Документация

- [Стратегия и план тестирования](docs/test-strategy.md)
- [Каталог тест-кейсов](docs/test-cases.md)
- [Структура Jira-бэклога](docs/jira-backlog.md)
- [Техническая спецификация AQA-фреймворка](docs/technical-specification.md)
- [Фактический MCP/JSON-RPC контракт сервера](docs/mcp-contract.md)
- [Реестр архитектурных рисков и мер контроля](docs/risk-register.md)

## Основной подход

1. Jenkins запускает Build container и собирает JAR основного проекта.
2. JAR передаётся в MCP Server container, где запускается STDIO Bridge.
3. AQA container подключается к Bridge по внутренней Docker network.
4. AQA Client регистрирует тестовую сессию через отдельный control channel Bridge до открытия MCP TCP-соединения.
5. Bridge связывает регистрацию с TCP-соединением, создаёт отдельный дочерний процесс MCP-сервера и фиксирует connectionId/PID.
6. MCP Client проверяет JSON-RPC/MCP, инструменты и интеграцию с Jira/Confluence.
6. Jenkins публикует JUnit XML и Allure Report, сохраняет диагностические логи Bridge и реестр созданных Atlassian-объектов, затем удаляет временные контейнеры и Docker-ресурсы.

## Важно

- Не использовать production-проекты, пространства и данные.
- Секреты хранить в локальных переменных окружения или Jenkins Credentials.
- Не направлять диагностические сообщения в MCP-поток: stdout дочернего процесса зарезервирован под JSON-RPC.
- AQA-контейнер не запускает JAR напрямую и не использует `ProcessBuilder` для управления процессом в другом контейнере.
- TCP Bridge доступен только внутри выделенной Docker network.
- Жизненный цикл дочерних процессов проверяется по отдельным диагностическим логам Bridge, а не по MCP-ответам. Корреляция runId/testId/clientSessionId/connectionId/PID выполняется через control channel, без изменения MCP-потока.
- Автоматические повторы неидемпотентных операций записи запрещены; неопределённый результат сначала сверяется по уникальному маркеру.
- Фактический MCP-контракт не содержит инструментов удаления Jira-задач и Confluence-страниц; первая версия AQA сохраняет реестр созданных объектов для последующей административной очистки и не удаляет их автоматически.
- Тесты должны быть независимы от реализации внутренних классов основного проекта.
