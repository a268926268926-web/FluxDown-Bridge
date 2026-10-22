package com.galbrena.fluxdownbridge;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class RootLauncher {
    private static final Object LOCK = new Object();
    private static final String ACTIVITY = "com.fluxdown.app/.ExternalDownloadActivity";

    private RootLauncher() {}

    static Result testRoot() {
        return run(Arrays.asList("su", "-c", "id"), 20);
    }

    static Result openFluxDown(String protocolUrl) {
        String safeUrl = protocolUrl.replace("'", "%27");
        String command = "am start --user current "
                + "-a android.intent.action.VIEW "
                + "-n " + ACTIVITY + " "
                + "-d '" + safeUrl + "'";
        synchronized (LOCK) {
            Result result = run(Arrays.asList("su", "-c", command), 20);
            if (result.code == 0 && !result.output.contains("Error:") && !result.output.contains("Exception")) {
                return result;
            }
            return result;
        }
    }

    private static Result run(List<String> command, int timeoutSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(124, "root request timed out");
            }
            String output;
            try (InputStream in = process.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) >= 0) bytes.write(buffer, 0, n);
                output = bytes.toString(StandardCharsets.UTF_8.name()).trim();
            }
            int code = process.exitValue();
            if (code == 0 && output.isEmpty()) output = "OK";
            return new Result(code, output);
        } catch (Exception e) {
            if (process != null) process.destroy();
            return new Result(-1, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static final class Result {
        final int code;
        final String output;

        Result(int code, String output) {
            this.code = code;
            this.output = output == null ? "" : output;
        }

        boolean ok() {
            return code == 0
                    && !output.contains("Permission denied")
                    && !output.contains("not allowed")
                    && !output.contains("does not exist")
                    && !output.contains("Error:")
                    && !output.contains("Exception");
        }
    }
}
