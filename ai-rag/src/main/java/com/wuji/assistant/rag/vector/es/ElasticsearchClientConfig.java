package com.wuji.assistant.rag.vector.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.wuji.assistant.rag.config.RagVectorProperties;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch Java Client（无认证）。
 *
 * @author liudy
 */
@Configuration
@ConditionalOnClass(ElasticsearchClient.class)
@ConditionalOnProperty(name = "wuji.rag.vector-backend", havingValue = "elasticsearch")
public class ElasticsearchClientConfig {

    @Bean(destroyMethod = "close")
    RestClient elasticsearchRestClient(RagVectorProperties properties) {
        List<HttpHost> hosts = parseHosts(properties.getElasticsearch().getUris());
        return RestClient.builder(hosts.toArray(HttpHost[]::new)).build();
    }

    @Bean
    ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    static List<HttpHost> parseHosts(String uris) {
        List<HttpHost> hosts = new ArrayList<>();
        if (!StringUtils.hasText(uris)) {
            hosts.add(HttpHost.create("http://127.0.0.1:9200"));
            return hosts;
        }
        for (String part : uris.split(",")) {
            String trimmed = part.trim();
            if (StringUtils.hasText(trimmed)) {
                hosts.add(HttpHost.create(trimmed));
            }
        }
        if (hosts.isEmpty()) {
            hosts.add(HttpHost.create("http://127.0.0.1:9200"));
        }
        return hosts;
    }
}
