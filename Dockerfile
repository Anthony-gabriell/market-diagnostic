FROM eclipse-temurin:17-jdk-alpine
# Em vez de compilar no Render, vamos usar o jar que você gera no IntelliJ
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]