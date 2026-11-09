package org.jeecg.modules.packagemanagement.application.legacy;
public class LegacyAjaxResult extends java.util.LinkedHashMap<String,Object> {
 public LegacyAjaxResult(int code,String message,Object data){put("code",code);put("msg",message);if(data!=null)put("data",data);}
 public static LegacyAjaxResult success(){return success("操作成功");}
 public static LegacyAjaxResult success(String msg){return new LegacyAjaxResult(200,msg,null);}
 public static LegacyAjaxResult success(Object data){return new LegacyAjaxResult(200,"操作成功",data);}
 public static LegacyAjaxResult error(){return error("操作失败");}
 public static LegacyAjaxResult error(String msg){return new LegacyAjaxResult(500,msg,null);}
 public boolean isError(){return Integer.valueOf(500).equals(get("code"));}
}
