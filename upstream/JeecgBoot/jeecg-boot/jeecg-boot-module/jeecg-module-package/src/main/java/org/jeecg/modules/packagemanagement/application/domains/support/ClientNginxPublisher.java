package org.jeecg.modules.packagemanagement.application.domains.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;

/** Writes the generated include and reloads nginx only when both settings exist. */
public final class ClientNginxPublisher {
    @FunctionalInterface
    public interface Shell { void run(List<String> command); }

    private final Shell shell;

    public ClientNginxPublisher() { this(ClientNginxPublisher::process); }

    public ClientNginxPublisher(Shell shell) { this.shell = shell; }

    public boolean configured(PackageManagementProperties settings) {
        return !include(settings).isBlank() && !reload(settings).isBlank();
    }

    public void publish(PackageManagementProperties settings, List<String> apexes) {
        String path = include(settings);
        String command = reload(settings);
        if (path.isBlank() || command.isBlank()) throw new IllegalStateException("nginx 未配置");
        Path file = Path.of(path);
        if (file.getParent() == null) throw new IllegalStateException("nginx 未配置");
        String config = ClientNginxConfig.render(apexes);
        String previous = null;
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) previous = Files.readString(file);
            Files.writeString(file, config);
            shell.run(command(command));
        } catch (IOException failure) {
            throw new IllegalStateException("nginx 写入失败");
        } catch (RuntimeException failure) {
            restore(file, previous);
            throw failure;
        }
    }

    static String include(PackageManagementProperties settings) {
        return value(settings.getDomainNginxInclude(), "DOMAIN_NGINX_INCLUDE");
    }

    static String reload(PackageManagementProperties settings) {
        return value(settings.getDomainNginxReload(), "DOMAIN_NGINX_RELOAD");
    }

    static List<String> command(String raw) {
        if (raw.chars().anyMatch(ch -> ";|&<>`$".indexOf(ch) >= 0 || ch == '\n' || ch == '\r')) {
            throw new IllegalStateException("nginx 重载命令无效");
        }
        return Arrays.asList(raw.trim().split("\\s+"));
    }

    private static String value(String property, String envName) {
        if (property != null && !property.isBlank()) return property.trim();
        String env = System.getenv(envName);
        return env == null ? "" : env.trim();
    }

    private static void restore(Path file, String previous) {
        try {
            if (previous == null) Files.deleteIfExists(file);
            else Files.writeString(file, previous);
        } catch (IOException ignored) {
            // The original reload error is the one the caller stores.
        }
    }

    private static void process(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                throw new IllegalStateException("nginx 重载失败");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("nginx 重载失败");
        } catch (IOException failure) {
            throw new IllegalStateException("nginx 重载失败");
        }
    }
}
