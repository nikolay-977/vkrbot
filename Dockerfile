# Этап сборки
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app

# Копируем файлы проекта
COPY pom.xml .
COPY src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

# Этап запуска
FROM openjdk:17-slim
WORKDIR /app

# Копируем собранный jar из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# Создаем пользователя для безопасности
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Порт приложения
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]