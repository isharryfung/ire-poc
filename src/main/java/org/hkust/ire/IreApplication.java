package org.hkust.ire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the Identity Resolution Engine (IRE).
 *
 * <p>Serves as both the Spring Boot main class and the WAR servlet initializer
 * for Tomcat deployment. Enables caching and scheduling.</p>
 *
 * <pre>
 *   mvn clean package
 *   cp target/ire.war /opt/tomcat/webapps/
 * </pre>
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class IreApplication extends SpringBootServletInitializer {

    /**
     * Configures the application for WAR deployment.
     *
     * @param application the SpringApplicationBuilder
     * @return configured builder
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(IreApplication.class);
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(IreApplication.class, args);
    }
}
