package org.modeling4genmusic.studio;

import org.modeling4genmusic.studio.config.StudioProperties;
import org.modeling4genmusic.studio.generation.GenerationProperties;
import org.modeling4genmusic.studio.generation.OllamaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ StudioProperties.class, OllamaProperties.class, GenerationProperties.class })
public class StudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudioApplication.class, args);
    }
}
