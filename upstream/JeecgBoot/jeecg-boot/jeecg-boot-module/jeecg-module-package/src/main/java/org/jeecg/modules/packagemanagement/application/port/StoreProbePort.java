package org.jeecg.modules.packagemanagement.application.port;

public interface StoreProbePort {
    Result getStatus(String url);

    record Result(boolean online, String reason) {
        public static Result listed() {
            return new Result(true, null);
        }

        public static Result delisted(String reason) {
            return new Result(false, reason);
        }
    }
}
