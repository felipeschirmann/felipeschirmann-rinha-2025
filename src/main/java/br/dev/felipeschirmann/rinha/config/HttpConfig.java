package br.dev.felipeschirmann.rinha.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;


@Configuration
public class HttpConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    // BEAN DO RESTCLIENT CORRIGIDO E DE ALTA PERFORMANCE
    @Bean
    public RestClient.Builder restClientBuilder(RinhaProperties rinhaProperties) {
        RinhaProperties.Webclient props = rinhaProperties.webclient();

        // 1. Cria um gerenciador de pool de conexões
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(props.maxConnections()); // Total de conexões no pool
        connectionManager.setDefaultMaxPerRoute(props.maxConnections()); // Total por host

        // 2. Cria o cliente Apache usando o gerenciador de pool
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        // 3. Cria a factory do Spring que usa o cliente Apache
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(props.connectTimeoutMs());
        // setReadTimeout foi depreciado, usamos setConnectionRequestTimeout
        factory.setConnectionRequestTimeout(props.responseTimeoutSec() * 1000);

        // 4. Retorna o RestClient.Builder usando a factory com pool
        return RestClient.builder().requestFactory(factory);
    }

    @Bean
    public JedisConnectionFactory redisConnectionFactory(RedisProperties redisProperties) {
        // Configura os detalhes básicos da conexão (host, porta)
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisProperties.getHost());
        config.setPort(redisProperties.getPort());

        // Configura o pool de conexões usando as propriedades do application.properties
        RedisProperties.Pool poolProps = redisProperties.getJedis().getPool();
        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
                .usePooling()
                .poolConfig(new GenericObjectPoolConfig<>())
                .and()
                .build();

        return new JedisConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Define que as chaves e valores serão serializados como Strings simples
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}