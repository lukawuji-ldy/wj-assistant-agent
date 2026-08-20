package com.wuji.assistant.rag.vector.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.wuji.assistant.common.exception.ErrorCode;
import com.wuji.assistant.common.exception.WujiException;
import com.wuji.assistant.rag.config.RagVectorProperties;
import com.wuji.assistant.rag.ingest.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.util.OptionalInt;

/**
 * ES 知识库索引生命周期：创建、维度校验与不一致时重建。
 *
 * @author liudy
 */
@Component
@ConditionalOnClass(ElasticsearchClient.class)
@ConditionalOnProperty(name = "wuji.rag.vector-backend", havingValue = "elasticsearch")
public class ElasticsearchIndexManager {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexManager.class);

    private final ElasticsearchClient client;
    private final RagVectorProperties properties;
    private final EmbeddingClient embeddingClient;

    public ElasticsearchIndexManager(ElasticsearchClient client,
                                       RagVectorProperties properties,
                                       ObjectProvider<EmbeddingClient> embeddingClients) {
        this.client = client;
        this.properties = properties;
        this.embeddingClient = embeddingClients.getIfAvailable(this::unavailableClient);
    }

    /**
     * 启动时确保索引存在且维度与当前 Embedding 配置一致。
     */
    public void ensureIndexOnStartup() {
        if (!embeddingClient.available()) {
            log.warn("Embedding client unavailable; skip Elasticsearch index initialization until first embed");
            return;
        }
        ensureIndexReady(false);
    }

    /**
     * 写入前确保索引维度可用；必要时按配置重建索引。
     */
    public void ensureIndexBeforeWrite() {
        if (!embeddingClient.available()) {
            throw new WujiException(ErrorCode.RAG_UNAVAILABLE, "Embedding 模型不可用，无法写入向量");
        }
        ensureIndexReady(true);
    }

    public int expectedDimensions() {
        return embeddingClient.dimensions();
    }

    public String indexName() {
        return properties.getElasticsearch().getIndexName();
    }

    public OptionalInt readIndexEmbeddingDimensions() {
        try {
            var response = client.indices().getMapping(m -> m.index(indexName()));
            IndexMappingRecord record = response.get(indexName());
            if (record == null || record.mappings() == null || record.mappings().properties() == null) {
                return OptionalInt.empty();
            }
            Property embedding = record.mappings().properties().get("embedding");
            if (embedding == null || !embedding.isDenseVector()) {
                return OptionalInt.empty();
            }
            Integer dims = embedding.denseVector().dims();
            return dims == null ? OptionalInt.empty() : OptionalInt.of(dims);
        } catch (IOException e) {
            throw new WujiException(ErrorCode.RAG_UNAVAILABLE, "读取 Elasticsearch mapping 失败: " + indexName(), e);
        }
    }

    private void ensureIndexReady(boolean fromWritePath) {
        String indexName = indexName();
        int expectedDims = expectedDimensions();
        try {
            boolean exists = client.indices().exists(idx -> idx.index(indexName)).value();
            if (!exists) {
                createIndex(expectedDims);
                return;
            }
            OptionalInt currentDims = readIndexEmbeddingDimensions();
            if (currentDims.isEmpty()) {
                log.warn("Elasticsearch index {} missing dense_vector embedding mapping; recreating", indexName);
                recreateIndex(expectedDims);
                return;
            }
            if (currentDims.getAsInt() == expectedDims) {
                return;
            }
            if (properties.getElasticsearch().isRecreateIndexOnDimensionMismatch()) {
                log.warn(
                        "Elasticsearch index {} embedding dims mismatch (index={}, expected={}); recreating index. "
                                + "All ES vector projections were cleared; rebuild embeddings for ACTIVE versions.",
                        indexName, currentDims.getAsInt(), expectedDims);
                recreateIndex(expectedDims);
                return;
            }
            throw dimensionMismatch(indexName, currentDims.getAsInt(), expectedDims, fromWritePath);
        } catch (IOException e) {
            throw new WujiException(ErrorCode.RAG_UNAVAILABLE,
                    "Elasticsearch index ensure failed: " + indexName, e);
        }
    }

    private void recreateIndex(int dims) throws IOException {
        deleteIndexIfExists();
        createIndex(dims);
    }

    private void deleteIndexIfExists() throws IOException {
        String indexName = indexName();
        if (client.indices().exists(idx -> idx.index(indexName)).value()) {
            client.indices().delete(d -> d.index(indexName));
            log.info("Elasticsearch index {} deleted", indexName);
        }
    }

    private void createIndex(int dims) throws IOException {
        String indexName = indexName();
        String indexJson = ElasticsearchIndexInitializer.buildIndexJson(dims);
        CreateIndexResponse response = client.indices()
                .create(create -> create.index(indexName).withJson(new StringReader(indexJson)));
        if (!response.acknowledged()) {
            throw new IllegalStateException("Failed to create index: " + indexName);
        }
        log.info("Elasticsearch index {} created with {} dimensions", indexName, dims);
    }

    private static WujiException dimensionMismatch(String indexName, int indexDims, int expectedDims,
                                                   boolean fromWritePath) {
        String action = fromWritePath
                ? "重建向量"
                : "启动";
        return new WujiException(ErrorCode.RAG_UNAVAILABLE, """
                Elasticsearch 索引 %s 的 embedding 维度为 %d，与当前 Embedding 模型维度 %d 不一致（%s失败）。
                请删除索引后重启，或设置 wuji.rag.elasticsearch.recreate-index-on-dimension-mismatch=true 后重试。
                """.formatted(indexName, indexDims, expectedDims, action).trim());
    }

    private EmbeddingClient unavailableClient() {
        return new EmbeddingClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public float[] embed(String text) {
                return null;
            }
        };
    }
}
