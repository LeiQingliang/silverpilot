package com.cecsmsserve.controller;

import com.cecsmsserve.entity.User;

/** Public registration accepts profile data only, never account IDs or roles. */
public record RegistrationRequest(
        String username, String password, String name, String telephone,
        String idNum, String sex, String address, String captchaId, String verificationCode) {

    public User toUser() {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setName(name);
        user.setTelephone(telephone);
        user.setIdNum(idNum);
        user.setSex(sex);
        user.setAddress(address);
        return user;
    }
}
