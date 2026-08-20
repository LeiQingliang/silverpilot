package com.cecsmsserve.util.result;

public class CommonResult<T> {
    private int code = 200;
    private String msg = "成功";
    private T result;
    private long timestamp = System.currentTimeMillis();

    public CommonResult() {}

    public CommonResult(T result) {
        this.result = result;
    }

    public CommonResult(String msg, T result) {
        this.msg = msg;
        this.result = result;
    }

    public CommonResult(int code, String msg, T result) {
        this.code = code;
        this.msg = msg;
        this.result = result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 操作成功
     */
    public static <T> CommonResult<T> success(T result) {
        return new CommonResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), result);
    }

    /**
     * 操作成功（带自定义消息）
     */
    public static <T> CommonResult<T> success(T result, String message) {
        return new CommonResult<>(ResultCode.SUCCESS.getCode(), message, result);
    }

    /**
     * 失败返回结果
     */
    public static <T> CommonResult<T> failed(String message) {
        return new CommonResult<>(ResultCode.FAILED.getCode(), message, null);
    }

    /**
     * 失败返回结果
     */
    public static <T> CommonResult<T> failed(ResultCode resultCode) {
        return new CommonResult<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /**
     * 失败返回结果
     */
    public static <T> CommonResult<T> failed(ResultCode resultCode, String message) {
        return new CommonResult<>(resultCode.getCode(), message, null);
    }

    /**
     * 错误返回结果（用于 AI 诊断等场景）
     */
    public static <T> CommonResult<T> error(String msg) {
        return new CommonResult<>(500, msg, null);
    }

    /**
     * 参数校验失败返回结果
     */
    public static <T> CommonResult<T> validateFailed() {
        return new CommonResult<>(ResultCode.VALIDATE_FAILED.getCode(), ResultCode.VALIDATE_FAILED.getMsg(), null);
    }

    /**
     * 参数校验失败返回结果
     */
    public static <T> CommonResult<T> validateFailed(String message) {
        return new CommonResult<>(ResultCode.VALIDATE_FAILED.getCode(), message, null);
    }

    /**
     * 未登录返回结果
     */
    public static <T> CommonResult<T> unauthorized() {
        return new CommonResult<>(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg(), null);
    }

    /**
     * 未登录返回结果
     */
    public static <T> CommonResult<T> unauthorized(String message) {
        return new CommonResult<>(ResultCode.UNAUTHORIZED.getCode(), message, null);
    }

    /**
     * 权限不足返回结果
     */
    public static <T> CommonResult<T> forbidden() {
        return new CommonResult<>(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMsg(), null);
    }

    /**
     * 权限不足返回结果
     */
    public static <T> CommonResult<T> forbidden(String message) {
        return new CommonResult<>(ResultCode.FORBIDDEN.getCode(), message, null);
    }

    /**
     * 资源不存在返回结果
     */
    public static <T> CommonResult<T> notFound() {
        return new CommonResult<>(ResultCode.NOT_FOUND.getCode(), ResultCode.NOT_FOUND.getMsg(), null);
    }

    /**
     * 资源不存在返回结果
     */
    public static <T> CommonResult<T> notFound(String message) {
        return new CommonResult<>(ResultCode.NOT_FOUND.getCode(), message, null);
    }

    /**
     * 数据冲突返回结果
     */
    public static <T> CommonResult<T> conflict(String message) {
        return new CommonResult<>(ResultCode.CONFLICT.getCode(), message, null);
    }

    /**
     * 用户名已存在返回结果
     */
    public static <T> CommonResult<T> usernameExist(T result) {
        return new CommonResult<>(ResultCode.usernameExist.getCode(), ResultCode.usernameExist.getMsg(), result);
    }

    /**
     * 手机号已存在返回结果
     */
    public static <T> CommonResult<T> phoneExist(T result) {
        return new CommonResult<>(ResultCode.telephoneExist.getCode(), ResultCode.telephoneExist.getMsg(), result);
    }

    public void setOK(T result) {
        this.code = 200;
        this.msg = "成功";
        this.result = result;
    }

    public void setNotFound() {
        this.code = 100;
        this.msg = "请求的资源不存在";
    }

    public void setNotInserted() {
        this.code = 101;
        this.msg = "插入失败";
    }

    public void setNotDelete() {
        this.code = 102;
        this.msg = "删除失败";
    }

    public void setNotUpdate() {
        this.code = 103;
        this.msg = "修改失败";
    }

    public void usernameExist() {
        this.code = 104;
        this.msg = "用户名已存在";
    }

    public void phoneExist() {
        this.code = 105;
        this.msg = "手机号已被注册";
    }

    public void pswError() {
        this.code = 106;
        this.msg = "密码错误！";
    }

    public void wrongAvatar() {
        this.code = 107;
        this.msg = "保存头像失败！";
    }

    public void set1(T result) {
        this.code = 111;
        this.msg = "社区管理员";
        this.result = result;
    }

    public void set2(T result) {
        this.code = 222;
        this.msg = "老年人用户";
        this.result = result;
    }

    public void set3(T result) {
        this.code = 333;
        this.msg = "社区工作者";
        this.result = result;
    }

    public void set4(T result) {
        this.code = 444;
        this.msg = "社区医生";
        this.result = result;
    }

    public void getNoUser() {
        this.code = 210;
        this.msg = "用户不存在";
    }
}
