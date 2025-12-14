# Настройка CI/CD в Github Actions
- Был выбран самописный эхо-сервер, который возвращает ту же строку, что пользователь передал в запросе

    ```bash
    $ curl -s http://localhost:8080/hello
    hello
    ```

- Код файла ci-cd.yml:

    ```yaml
    name: CI/CD
    
    # События для запуска workflow
    on:
      # Workflow будет запущен на пуш в ветку main или на pull-request в ветку main
      push:
        branches: [ "main" ]
      pull_request:
        branches: [ "main" ]
    
    # Шаги workflow
    jobs:
      # Тестирование проекта на разных ОС
      test:
        # Тип runner'а, который будет использоваться для тестирования проекта
        runs-on: ${{ matrix.os }}
        strategy:
          matrix:
            os: [ ubuntu-latest, windows-latest, macos-latest ]
    
        # Шаги workflow сборки проекта
        steps:
          # Клонировать репозиторий на раннер и сделать check-out на коммит, который вызвал workflow
          - uses: actions/checkout@v4
          # Настроить Java 21 для прогона тестов
          - uses: actions/setup-java@v3
            with:
              distribution: temurin
              java-version: 21
          # Запустить прогон тестов с распечаткой ошибок
          - run: mvn clean test -e
      # Сборка проекта и публикация образа в реестре
      install:
        # Сборка и публикация только после успешных тестов
        needs: test
        # Данные для авторизации на хранилище образов GitHub Container Registry
        env:
          GHCR_USERNAME: ${{ secrets.GHCR_USERNAME }}
          GHCR_TOKEN: ${{ secrets.GHCR_TOKEN }}
        # Тип runner'а, который будет использоваться для сборки проекта
        runs-on: ubuntu-latest
    
        # Шаги workflow сборки проекта
        steps:
          # Клонировать репозиторий на раннер и сделать check-out на коммит, который вызвал workflow
          - uses: actions/checkout@v4
          # Настроить Java 21 для сборки
          - uses: actions/setup-java@v3
            with:
              distribution: temurin
              java-version: 21
          # Запустить сборку без тестов и с распечаткой ошибок
          - run: mvn clean install -DskipTests -e
      # Тестирование проекта на разных ОС
      deploy:
        # Развёртывание приложения только после успешной публикации образа
        needs: install
        # Тип runner'а, который будет использоваться для тестирования проекта
        runs-on: ubuntu-latest
        # Шаги workflow развёртывания проекта
        steps:
          # Развёртывание на удалённом сервере с помощью SSH
          - name: Deploy to remote server
            uses: appleboy/ssh-action@v0.1.7
            with:
              host: ${{ secrets.REMOTE_HOST }}
              username: ${{ secrets.REMOTE_USER }}
              key: ${{ secrets.SSH_KEY }}
              script: |
                sudo docker login ghcr.io -u ${{ secrets.GHCR_USERNAME }} -p ${{ secrets.GHCR_TOKEN }}
                sudo docker pull ghcr.io/dfedorino/github-actions-echo-server/echo-server:latest
                sudo docker stop echo-server || true
                sudo docker rm echo-server || true
                sudo docker run -d \
                  --name echo-server \
                  -p 8080:8080 \
                  ghcr.io/dfedorino/github-actions-echo-server/echo-server:latest
    
    ```

- Описание настроенных джоб:
    - `test` - запуск тестов на 3 операционных системах - Linux, macOS, Windows, используется matrix strategy, которая позволяет запускать джобу на 3 раннерах параллельно

      Шаги:

        1. `actions/checkout@v4` - клонирует репозиторий на runner, делает checkout коммита, который стриггерил workflow
        2. `actions/setup-java@v3` - устанавливает JDK 21 (Temurin)
        3. `mvn clean test`
            - Полная очистка
            - Запуск тестов
    - `install` - сборка проекта и публикация Docker-образа в GitHub Container Registry через Jib Maven Plugin, запускается только если test успешен на всех ОС. Переменные для авторизации в GitHub Container Registry хранятся в GitHub Secrets и передаются в сборку с помощью env переменных:

        ```yaml
        env:
          GHCR_USERNAME: ${{ secrets.GHCR_USERNAME }}
          GHCR_TOKEN: ${{ secrets.GHCR_TOKEN }}
        ```

      Шаги:

        1. `actions/checkout@v4` - клонирует репозиторий на runner, делает checkout коммита, который стриггерил workflow
        2. `actions/setup-java@v3` - устанавливает JDK 21 (Temurin)
        3. `mvn clean install -DskipTests`
            - Сборка JAR
            - Запуск `jib:build` (привязан к lifecycle), который создаёт Docker-образ
            - Пуш образа в реестр образов GitHub
    - `deploy` - развёртывание приложения на удалённом сервере, запускается только после успешной публикации образа

      Шаги:

      `appleboy/ssh-action@v0.1.7` - открывает SSH-сессию на удалённый сервер, выполняет заданный скрипт на сервере.

      Выполняемые команды в скрипте:

        - `docker login [ghcr.io](http://ghcr.io/)` - Аутентификация к GHCR с использованием PAT
        - `docker pull` - Получение последней версии образа
        - `docker stop` / `docker rm` - Остановка и удаление старого контейнера
        - `docker run -d -p 8080:8080` - Запуск нового контейнера, проброс порта

    - Workflow запускается при:
        - любом `push` в ветку `main`
        - любом `pull_request`, направленном в ветку `main`
    - Зависимости между джобами:
        - `test` запускается первой
        - `install` зависит от `test` и запускается только после успешного прогона тестов на всех ОС
        - `deploy` зависит от `install` и запускается только после успешной публикации образа в реестр
- Ссылка на репозиторий: https://github.com/dfedorino/github-actions-echo-server
