FROM eclipse-temurin:21-jre-alpine
EXPOSE 8085
COPY target/*.jar user.jar
ENTRYPOINT ["java","-jar","/user.jar"]