package com.ragnews.search.embedding;

import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Singleton
public class SimpleHashEmbeddingGenerator implements EmbeddingGenerator {

    public static final int DIMENSION = 16;

    @Override
    public List<Double> generateEmbedding(String text) {
        double[] vector = new double[DIMENSION];
        String normalizedText = text == null ? "" : text.toLowerCase(Locale.ROOT);

        for (String token : normalizedText.split("[^a-z0-9]+")) {
            if (token.isBlank()) {
                continue;
            }

            int hash = token.hashCode();
            int index = Math.floorMod(hash, DIMENSION);
            double direction = hash % 2 == 0 ? 1.0 : -1.0;
            vector[index] += direction;
        }

        normalize(vector);
        return toList(vector);
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
