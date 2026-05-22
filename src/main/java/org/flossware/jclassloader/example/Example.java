package org.flossware.jclassloader.example;

import org.flossware.jclassloader.AuthConfig;
import org.flossware.jclassloader.FtpClassSource;
import org.flossware.jclassloader.JClassLoader;
import org.flossware.jclassloader.cache.FileSystemCache;

import java.io.IOException;

/**
 * Basic usage examples for JClassLoader.
 * Demonstrates local, remote (HTTP/HTTPS), and FTP class loading with caching.
 */
public class Example {

    public static void main(String[] args) throws Exception {
        basicLocalExample();
        remoteWithCacheExample();
        multipleSourcesExample();
        ftpExample();
    }

    public static void basicLocalExample() throws Exception {
        System.out.println("=== Basic Local Class Loading ===");

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource("./classes")
            .useCache(false)
            .build();

        System.out.println("ClassLoader created with local source");
        System.out.println("Sources: " + loader.getClassSources().size());
    }

    public static void remoteWithCacheExample() throws IOException {
        System.out.println("\n=== Remote Class Loading with Cache ===");

        FileSystemCache cache = new FileSystemCache("/tmp/jclassloader-cache");

        AuthConfig auth = AuthConfig.bearer("my-secret-token");

        JClassLoader loader = JClassLoader.builder()
            .addRemoteSource("https://example.com/classes/", auth)
            .cache(cache)
            .useCache(true)
            .build();

        System.out.println("ClassLoader created with remote source and cache");
        System.out.println("Cache enabled: " + loader.isCacheEnabled());
        System.out.println("Cache location: " + cache.getCacheDirectory());
    }

    public static void multipleSourcesExample() throws IOException {
        System.out.println("\n=== Multiple Class Sources ===");

        JClassLoader loader = JClassLoader.builder()
            .addLocalSource("/opt/app/classes")
            .addRemoteSource("https://cdn.example.com/classes/")
            .addRemoteSource("https://backup.example.com/classes/",
                           AuthConfig.basic("user", "pass"))
            .cache(new FileSystemCache("/tmp/cache"))
            .build();

        System.out.println("ClassLoader created with multiple sources:");
        for (int i = 0; i < loader.getClassSources().size(); i++) {
            System.out.println("  " + (i+1) + ". " + loader.getClassSources().get(i).getDescription());
        }
    }

    public static void ftpExample() {
        System.out.println("\n=== FTP/FTPS Class Loading ===");

        JClassLoader loader = JClassLoader.builder()
            .addClassSource(new FtpClassSource("ftp://ftp.example.com/classes/"))
            .addClassSource(new FtpClassSource("ftps://secure.example.com/classes/",
                                              "username", "password"))
            .useCache(true)
            .build();

        System.out.println("ClassLoader created with FTP sources:");
        for (int i = 0; i < loader.getClassSources().size(); i++) {
            System.out.println("  " + (i+1) + ". " + loader.getClassSources().get(i).getDescription());
        }
    }
}
