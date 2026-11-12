pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        SERVER_REPOSITORY = 'https://github.com/Solomentcev/atlassian-mcp-light.git'
        SERVER_BRANCH = 'master'
        DOCKER_IMAGE_NAME = 'atlassian-mcp-light'
        AQA_CREDENTIALS_ID = 'atlassian-mcp-env'
    }

    stages {
        stage('Checkout AQA') {
            steps {
                checkout scm
                script {
                    env.RUN_ID = "${env.JOB_NAME}-${env.BUILD_NUMBER}".replaceAll(/[^a-zA-Z0-9_.-]/, '-')
                    env.DOCKER_IMAGE = "${env.DOCKER_IMAGE_NAME}:${env.RUN_ID}"
                }
            }
        }

        stage('Checkout server') {
            steps {
                dir('server') {
                    git branch: "${env.SERVER_BRANCH}", url: "${env.SERVER_REPOSITORY}"
                    script {
                        env.SERVER_COMMIT = sh(
                            script: 'git rev-parse HEAD',
                            returnStdout: true
                        ).trim()
                    }
                }
                writeFile(
                    file: 'server-revision.txt',
                    text: "Repository: ${env.SERVER_REPOSITORY}\nBranch: ${env.SERVER_BRANCH}\nCommit: ${env.SERVER_COMMIT}\n"
                )
                echo "Server revision: ${env.SERVER_COMMIT}"
            }
        }

        stage('Build Docker image') {
            steps {
                dir('server') {
                    sh 'docker build --pull -t "$DOCKER_IMAGE" .'
                }
            }
        }

        stage('Integration tests') {
            steps {
                withCredentials([file(
                    credentialsId: "${env.AQA_CREDENTIALS_ID}",
                    variable: 'ATLASSIAN_ENV_FILE'
                )]) {
                    withEnv([
                        'AQA_SERVER_MODE=docker',
                        "AQA_DOCKER_IMAGE=${env.DOCKER_IMAGE}",
                        "AQA_DOCKER_ENV_FILE=${env.ATLASSIAN_ENV_FILE}",
                        "AQA_RUN_ID=${env.RUN_ID}"
                    ]) {
                        sh 'mvn -B -Pintegration clean test'
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                junit(
                    testResults: 'target/surefire-reports/*.xml',
                    allowEmptyResults: true
                )

                archiveArtifacts(
                    artifacts: 'server-revision.txt',
                    allowEmptyArchive: true,
                    fingerprint: true
                )

                if (fileExists('target/allure-results')) {
                    allure(
                        includeProperties: false,
                        results: [[path: 'target/allure-results']]
                    )
                }

                if (env.RUN_ID) {
                    sh(
                        script: '''
                            containers=$(docker ps -aq --filter "name=aml-${RUN_ID}" || true)
                            if [ -n "$containers" ]; then
                                echo "$containers" | xargs -r docker rm -f
                            fi
                        ''',
                        returnStatus: true
                    )
                }

                if (env.DOCKER_IMAGE) {
                    sh(
                        script: 'docker image rm "$DOCKER_IMAGE" || true',
                        returnStatus: true
                    )
                }
            }
        }
    }
}
