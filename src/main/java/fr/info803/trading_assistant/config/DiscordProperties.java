package fr.info803.trading_assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

/**
 * Propriétés de configuration pour Discord.
 */
@Configuration
@ConfigurationProperties(prefix = "discord")
@Getter
@Setter
public class DiscordProperties {
    private String webhookUrl;
}
