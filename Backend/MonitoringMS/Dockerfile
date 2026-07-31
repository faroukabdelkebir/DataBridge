FROM eclipse-temurin:21-jre-alpine
EXPOSE 65010
ADD target/MonitoringMS-0.0.1-SNAPSHOT.jar monitoring.jar
ENTRYPOINT ["java","-jar","/monitoring.jar"]