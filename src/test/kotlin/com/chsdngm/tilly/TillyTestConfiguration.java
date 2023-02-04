package com.chsdngm.tilly;

import com.chsdngm.tilly.metrics.MetricsUtils;
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.spring.autoconfigure.vision.CloudVisionAutoConfiguration;
import com.google.cloud.spring.core.DefaultCredentialsProvider;
import com.google.cloud.spring.core.UserAgentHeaderProvider;
import com.google.cloud.spring.vision.CloudVisionTemplate;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

// Will be used in the future
@TestConfiguration
public class TillyTestConfiguration {
    @Bean
    public CloudVisionTemplate imageAnnotatorClient() throws IOException {
        return Mockito.mock(CloudVisionTemplate.class, "vlad");
    }

    @Bean
    public CredentialsProvider googleCredentials() {
        return Mockito.mock(CredentialsProvider.class);
    }

    @Bean
    public MetricsUtils metricsUtils() {
        return Mockito.mock(MetricsUtils.class);
    }
}
