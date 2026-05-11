# Этап сборки (Maven + Corretto 17)
FROM maven:3.8.4-amazoncorretto-17 AS build
WORKDIR /app

# Копируем файлы проекта
COPY pom.xml .
COPY src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

# Этап запуска (только Corretto 17, slim version via Alpine)
FROM amazoncorretto:17-alpine
WORKDIR /app

# Копируем собранный jar из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# Создаем пользователя для безопасности
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1000 && \
    chown -R appuser:appgroup /app
USER appuser

# Порт приложения
EXPOSE 8080

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]