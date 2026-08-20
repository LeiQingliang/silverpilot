package com.cecsmsserve.entity.vo;

import com.alibaba.excel.annotation.ExcelProperty;

public class SignedUserList {

//    @ExcelIgnore //导出时忽略这个字段
//    private String id;

    @ExcelProperty(value = {"姓名"})
    private  String name;

    @ExcelProperty(value = {"性别"})
    private String sex;

    @ExcelProperty(value = {"年龄"})
    private Integer age;

    @ExcelProperty(value = {"手机号"})
    private String telephone;

    @ExcelProperty(value = {"签到"})
    private String singin;

    public SignedUserList() {
    }

    public SignedUserList(String name, String sex, Integer age, String telephone, String singin) {
        this.name = name;
        this.sex = sex;
        this.age = age;
        this.telephone = telephone;
        this.singin = singin;
    }

//    public String getId() {
//        return id;
//    }

//    public void setId(String id) {
//        this.id = id;
//    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getSingin() {
        return singin;
    }

    public void setSingin(String singin) {
        this.singin = singin;
    }

    @Override
    public String toString() {
        return "SignedUserList{" +
//                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", sex='" + sex + '\'' +
                ", age=" + age +
                ", telephone='" + telephone + '\'' +
                ", singin='" + singin + '\'' +
                '}';
    }
}
