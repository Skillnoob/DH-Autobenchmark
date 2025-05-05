package com.skillnoob.dh.benchmark.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DownloadManager {
    /**
     * Downloads a file from a URL to a specified save path.
     */
    public static boolean downloadFile(String url, String savePath, String fileName) throws IOException, InterruptedException {
        Path path = Paths.get(savePath, fileName);
        if (Files.exists(path)) {
            return false;
        }

        FileManager.ensureDirectoryExists(savePath);

        System.out.println("Downloading " + fileName + " from " + url + ".");
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                Files.write(path, response.body());
                System.out.println("Downloaded " + fileName + " successfully.");
                return true;
            } else {
                System.err.println("Failed to download " + fileName + " (status code " + response.statusCode() + ").");
                return false;
            }
        }
    }
}
