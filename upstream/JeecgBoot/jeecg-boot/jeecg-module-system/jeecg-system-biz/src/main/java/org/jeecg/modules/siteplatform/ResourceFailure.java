package org.jeecg.modules.siteplatform;

/** Safe messages only: do not include SQL, credentials or DNS challenge values. */
public class ResourceFailure extends RuntimeException {
    private final int status;
    public ResourceFailure(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
