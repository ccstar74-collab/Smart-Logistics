package com.smartlogistics.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class KnowledgeBase {
    private final Path directory;
    private final AtomicReference<List<Chunk>> chunks = new AtomicReference<List<Chunk>>(Collections.<Chunk>emptyList());

    KnowledgeBase(Path directory) { this.directory = directory; }

    synchronized int reload() throws IOException {
        Files.createDirectories(directory);
        List<Chunk> loaded = new ArrayList<Chunk>();
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> paths = files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".md") || name.endsWith(".txt");
                    })
                    .sorted()
                    .collect(Collectors.toList());
            for (Path path : paths) {
                String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                loaded.addAll(split(path.getFileName().toString(), content));
            }
        }
        chunks.set(Collections.unmodifiableList(loaded));
        return loaded.size();
    }

    synchronized void addDocument(String title, String content) throws IOException {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("title 不能为空");
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("content 不能为空");
        Files.createDirectories(directory);
        String safeName = title.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        if (safeName.isEmpty()) safeName = "document-" + System.currentTimeMillis();
        if (!safeName.endsWith(".md")) safeName += ".md";
        Path target = directory.resolve(safeName).normalize();
        if (!target.getParent().equals(directory.toAbsolutePath().normalize()) && directory.isAbsolute()) {
            throw new IllegalArgumentException("非法文档名称");
        }
        Files.write(target, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        reload();
    }

    List<SearchResult> search(String query, int limit) {
        Set<String> queryTerms = terms(query);
        if (queryTerms.isEmpty()) return Collections.emptyList();
        List<SearchResult> results = new ArrayList<SearchResult>();
        List<Chunk> snapshot = chunks.get();
        Map<String, Integer> documentFrequency = new HashMap<String, Integer>();
        for (String term : queryTerms) {
            int count = 0;
            for (Chunk chunk : snapshot) if (chunk.termFrequency.containsKey(term)) count++;
            documentFrequency.put(term, count);
        }
        for (Chunk chunk : snapshot) {
            double score = 0;
            for (String term : queryTerms) {
                int tf = chunk.termFrequency.containsKey(term) ? chunk.termFrequency.get(term) : 0;
                if (tf == 0) continue;
                double idf = Math.log(1.0 + (snapshot.size() + 1.0) / (documentFrequency.get(term) + 1.0));
                score += (1.0 + Math.log(tf)) * idf;
            }
            if (score > 0) results.add(new SearchResult(chunk.source, chunk.text, score));
        }
        Collections.sort(results, Comparator.comparingDouble(SearchResult::getScore).reversed());
        return results.subList(0, Math.min(Math.max(limit, 0), results.size()));
    }

    int size() { return chunks.get().size(); }

    private static List<Chunk> split(String source, String content) {
        List<Chunk> result = new ArrayList<Chunk>();
        String[] paragraphs = content.replace("\r", "").split("\n\\s*\n");
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String clean = paragraph.trim();
            if (clean.isEmpty()) continue;
            if (current.length() > 0 && current.length() + clean.length() > 900) {
                result.add(new Chunk(source, current.toString()));
                current.setLength(0);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(clean);
        }
        if (current.length() > 0) result.add(new Chunk(source, current.toString()));
        return result;
    }

    private static Set<String> terms(String text) {
        HashSet<String> result = new HashSet<String>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder();
        StringBuilder chinese = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (isCjk(c)) {
                flushLatin(latin, result);
                chinese.append(c);
            } else {
                flushChinese(chinese, result);
                if (Character.isLetterOrDigit(c)) latin.append(c);
                else flushLatin(latin, result);
            }
        }
        flushLatin(latin, result);
        flushChinese(chinese, result);
        return result;
    }

    private static void flushLatin(StringBuilder value, Set<String> result) {
        if (value.length() > 1) result.add(value.toString());
        value.setLength(0);
    }

    private static void flushChinese(StringBuilder value, Set<String> result) {
        for (int i = 0; i < value.length(); i++) {
            result.add(String.valueOf(value.charAt(i)));
            if (i + 1 < value.length()) result.add(value.substring(i, i + 2));
        }
        value.setLength(0);
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A;
    }

    static final class SearchResult {
        final String source;
        final String text;
        final double score;
        SearchResult(String source, String text, double score) {
            this.source = source; this.text = text; this.score = score;
        }
        double getScore() { return score; }
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("source", source);
            map.put("score", Math.round(score * 1000.0) / 1000.0);
            return map;
        }
    }

    private static final class Chunk {
        final String source;
        final String text;
        final Map<String, Integer> termFrequency;
        Chunk(String source, String text) {
            this.source = source;
            this.text = text;
            this.termFrequency = new HashMap<String, Integer>();
            for (String term : terms(text)) termFrequency.put(term, count(text, term));
        }
        private static int count(String text, String term) {
            String source = text.toLowerCase(Locale.ROOT);
            int count = 0, index = 0;
            while ((index = source.indexOf(term, index)) >= 0) { count++; index += term.length(); }
            return count;
        }
    }
}

