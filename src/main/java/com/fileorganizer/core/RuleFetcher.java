package com.fileorganizer.core;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

public class RuleFetcher {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Downloads raw JSON from an http(s) or file:// URL. */
    public static String download(String url) throws IOException, InterruptedException {
        if (url.startsWith("file:")) {
            try {
                return Files.readString(Paths.get(URI.create(url)));
            } catch (Exception e) {
                throw new IOException("Cannot read local file: " + e.getMessage(), e);
            }
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "CloudSort/1.0")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    public static RuleEngine parse(String json) throws JsonSyntaxException {
        RuleEngine engine = new Gson().fromJson(json, RuleEngine.class);
        if (engine == null || engine.getCategories() == null || engine.getCategories().isEmpty()) {
            throw new JsonSyntaxException("JSON has no categories");
        }
        return engine;
    }

    public static RuleEngine fetch(String url) throws IOException, InterruptedException {
        String json = download(url);
        return parse(json);
    }
}