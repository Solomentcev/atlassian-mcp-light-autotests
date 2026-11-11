package ru.solomentsev.aqa.testdata;

/** Тестовые данные для сценариев Jira. */
public final class JiraTestData {

    public static final String ISSUE_KEY = "AQA-1";
    public static final int INVALID_ISSUE_KEY_NUMBER = 42;
    public static final String PROJECT_KEY = "AQA";
    public static final String SEARCH_JQL = "project = " + PROJECT_KEY;

    private JiraTestData() {
    }
}
