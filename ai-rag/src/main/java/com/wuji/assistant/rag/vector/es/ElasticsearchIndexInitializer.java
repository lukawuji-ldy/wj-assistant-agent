package com.wuji.assistant.rag.vector.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ES index 启动初始化（IK mapping；dims 来自 Embedding 配置）。
 *
 * @author liudy
 */
@Component
@ConditionalOnClass(ElasticsearchClient.class)
@ConditionalOnProperty(name = "wuji.rag.vector-backend", havingValue = "elasticsearch")
public class ElasticsearchIndexInitializer {

    private final ElasticsearchIndexManager indexManager;

    public ElasticsearchIndexInitializer(ElasticsearchIndexManager indexManager) {
        this.indexManager = indexManager;
    }

    @PostConstruct
    public void init() {
        indexManager.ensureIndexOnStartup();
    }

    static String buildIndexJson(int dims) {
        return """
                {
                  "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
                  "mappings": {
                    "properties": {
                      "content": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_smart"
                      },
                      "embedding": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine"
                      },
                      "chunk_id": { "type": "keyword" },
                      "doc_id": { "type": "keyword" },
                      "version_id": { "type": "keyword" },
                      "collection": { "type": "keyword" },
                      "status": { "type": "keyword" },
                      "version_status": { "type": "keyword" },
                      "revision": { "type": "keyword" },
                      "content_hash": { "type": "keyword" },
                      "section": { "type": "text" },
                      "summary": { "type": "keyword" }
                    }
                  }
                }
                """.formatted(dims);
    }
}
