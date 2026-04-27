package com.example.m1nd.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class PatternEmbeddingService {
    private static final int DIMENSIONS = 16;

    public double[] embed(String text) {
        String normalized = text == null ? "" : text.toLowerCase().trim();
        String[] tokens = normalized.isBlank() ? new String[0] : normalized.split("\\s+");
        double[] vector = new double[DIMENSIONS];
        for (String token : tokens) {
            int idx = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[idx] += 1.0;
        }
        normalize(vector);
        return vector;
    }

    public String toJson(double[] vector) {
        return Arrays.toString(vector);
    }

    public double[] fromJson(String embeddingJson) {
        String raw = embeddingJson == null ? "" : embeddingJson.trim();
        if (raw.length() < 2) {
            return new double[DIMENSIONS];
        }
        String body = raw.substring(1, raw.length() - 1).trim();
        if (body.isBlank()) {
            return new double[DIMENSIONS];
        }
        String[] parts = body.split(",");
        List<Double> values = new ArrayList<>();
        for (String part : parts) {
            values.add(Double.parseDouble(part.trim()));
        }
        double[] vector = new double[DIMENSIONS];
        for (int i = 0; i < Math.min(values.size(), DIMENSIONS); i++) {
            vector[i] = values.get(i);
        }
        normalize(vector);
        return vector;
    }

    public double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void normalize(double[] vector) {
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        if (norm == 0.0) {
            return;
        }
        double sqrtNorm = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= sqrtNorm;
        }
    }
}
