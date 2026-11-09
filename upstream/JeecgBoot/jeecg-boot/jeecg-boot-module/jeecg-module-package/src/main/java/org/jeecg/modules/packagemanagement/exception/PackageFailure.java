package org.jeecg.modules.packagemanagement.exception;
public class PackageFailure extends RuntimeException {
 private final int code;
 public PackageFailure(int code,String message){super(message);this.code=code;}
 public PackageFailure(String message,Throwable cause){super(message,cause);this.code=502;}
 public int getCode(){return code;}
}
