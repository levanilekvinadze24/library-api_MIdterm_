package ge.kiu.library.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI libraryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Library Management API")
                .version("1.0.0")
                .description("Spring Boot final project: Authors, Books, Categories and Reviews with "
                        + "validation, pagination & sorting, and cover image upload."));
    }
}
