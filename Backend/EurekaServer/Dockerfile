FROM eclipse-temurin:21-jre-alpine
EXPOSE 8761
COPY target/*.jar eureka.jar
ENTRYPOINT ["java","-jar","/eureka.jar"]