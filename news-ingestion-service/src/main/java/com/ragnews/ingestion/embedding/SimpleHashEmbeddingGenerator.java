package com.ragnews.ingestion.embedding;

import com.ragnews.ingestion.parser.NormalizedArticle;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Singleton
@Requires(property = "embedding.provider", value = "simple-hash")
public class SimpleHashEmbeddingGenerator implements EmbeddingGenerator {

    private final int dimensions;

    public SimpleHashEmbeddingGenerator(@Value("${embedding.dimensions}") int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("embedding.dimensions must be greater than zero");
        }

        this.dimensions = dimensions;
    }

    @Override
    public List<Double> generate(NormalizedArticle article) {
        double[] vector = new double[dimensions];
        String text = buildText(article);

        for (String token : text.split("[^a-z0-9]+")) {
            if (token.isBlank()) {
                continue;
            }

            int hash = token.hashCode();
            int index = Math.floorMod(hash, dimensions);
            double direction = hash % 2 == 0 ? 1.0 : -1.0;
            vector[index] += direction;
        }

        normalize(vector);
        return toList(vector);
    }

    private String buildText(NormalizedArticle article) {
        String title = article.title() == null ? "" : article.title();
        String content = article.content() == null ? "" : article.content();

        return (title + " " + content).toLowerCase(Locale.ROOT);
    }

    private void normalize(double[] vector) {
        double squaredSum = 0.0;

        for (double value : vector) {
            squaredSum += value * value;
        }

        if (squaredSum == 0.0) {
            return;
        }

        double length = Math.sqrt(squaredSum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / length;
        }
    }

    private List<Double> toList(double[] vector) {
        List<Double> values = new ArrayList<>(vector.length);

        for (double value : vector) {
            values.add(value);
        }

        return values;
    }
}
