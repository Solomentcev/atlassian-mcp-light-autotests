package ru.solomentsev.aqa.testdata;

/** Тестовые данные для сценариев Jira. */
public final class JiraTestData {

    private static final String ISSUE_KEY = "AQA-1";
    private static final int INVALID_ISSUE_KEY_NUMBER = 42;
    private static final String PROJECT_KEY = "AQA";
    private static final String SEARCH_JQL = "project = " + PROJECT_KEY;

    public static String getSearchJql() { return SEARCH_JQL; }

    public static String getIssueKey() {
        return ISSUE_KEY;
    }

    public static int getInvalidIssueKeyNumber() {
        return INVALID_ISSUE_KEY_NUMBER;
    }

    public static String getProjectKey() {
        return PROJECT_KEY;
    }

    public static String getSearchJql() {
        return SEARCH_JQL;
    }

    private JiraTestData() {
    }
}
