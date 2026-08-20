package com.cecsmsserve.util.result;

public enum ResultCode implements IErrorCode{

    SUCCESS(200,"操作成功"),
    FAILED(500,"操作失败"),
    VALIDATE_FAILED(400,"参数校验失败"),
    UNAUTHORIZED(401,"未登录或登录已过期"),
    FORBIDDEN(403,"权限不足"),
    NOT_FOUND(404,"资源不存在"),
    METHOD_NOT_ALLOWED(405,"请求方法不支持"),
    CONFLICT(409,"数据冲突"),
    INTERNAL_SERVER_ERROR(500,"服务器内部错误"),
    SERVICE_UNAVAILABLE(503,"服务不可用"),
    usernameExist(104,"用户名已存在"),
    telephoneExist(105,"手机号已被注册");

    private ResultCode(int code,String msg){
        this.code=code;
        this.msg=msg;
    }

    private int code=200;
    private String msg="成功";

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
