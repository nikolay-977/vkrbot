#!/bin/bash

# Сборка приложения
echo "Building application..."
mvn clean package -DskipTests

# Остановка старых контейнеров
echo "Stopping old containers..."
docker-compose -f docker-compose.dev.yml down

# Запуск новых контейнеров
echo "Starting new containers..."
docker-compose -f docker-compose.dev.yml up -d --build

# Проверка статуса
echo "Checking status..."
docker-compose -f docker-compose.dev.yml ps

echo "Deployment complete!"