package com.cecsmsserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cecsmsserve.entity.User;
import com.cecsmsserve.mapper.UserMapper;
import com.cecsmsserve.service.IUserService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.cecsmsserve.util.JWTUtil;
import com.cecsmsserve.util.result.CommonResult;
import com.cecsmsserve.util.result.ResultCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author GoatCode
 * @since 2024-07-15
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;
    private final JWTUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, JWTUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public CommonResult<User> register(User user) {
        String validationError = validateRegistration(user);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        user.setUsername(user.getUsername().trim());
        user.setName(user.getName().trim());
        user.setTelephone(user.getTelephone().trim());
        user.setIdNum(user.getIdNum().trim());
        CommonResult<User> result = new CommonResult<>();
        LambdaQueryWrapper<User> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(User::getUsername, user.getUsername());
        User exist1 = userMapper.selectOne(wrapper1);

        LambdaQueryWrapper<User> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(User::getTelephone, user.getTelephone());
        User exist2 = userMapper.selectOne(wrapper2);

        if (exist1 != null) {
            result.usernameExist(); //104
        } else if (exist2 != null) {
            result.phoneExist(); //105
        } else {
            user.setRoleId(4);
            user.setPassword(encodePassword(user.getPassword()));
            String birthdayString = user.getIdNum().substring(6, 14);
            LocalDate birthday = LocalDate.parse(birthdayString, DateTimeFormatter.BASIC_ISO_DATE);
            user.setBirthday(birthday);
            user.setAge(Period.between(birthday, LocalDate.now()).getYears());
            int i = userMapper.insert(user);
            if (i <= 0) {
                result.setNotInserted(); //101
            } else {
                result.setOK(user);
            }
        }
        return result;
    }

    @Override
    public CommonResult<User> login(String loginName, String password) {
        if (loginName == null || loginName.isBlank() || password == null || password.isBlank()) {
            return CommonResult.validateFailed("用户名和密码不能为空");
        }
        String phone = "^[1][23456789]\\d{9}$";
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (loginName.matches(phone)) {
            wrapper.eq(User::getTelephone, loginName);
        } else {
            wrapper.eq(User::getUsername, loginName);
        }

        User user = userMapper.selectOne(wrapper);
        CommonResult<User> result = new CommonResult<>();
        if (user == null || !passwordMatches(password, user.getPassword())) {
            result.setNotFound();
            return result;
        }

        if (!isBcryptHash(user.getPassword())) {
            user.setPassword(encodePassword(password));
            userMapper.updateById(user);
        }

        user.setToken(jwtUtil.buildToken(user.getId()));
        user.setPassword(null);
        result.setOK(user);
        return result;
    }

    @Override
    public CommonResult<User> insert(User user) {
        String validationError = validateManagedUser(user);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        user.setUsername(user.getUsername().trim());
        user.setName(user.getName().trim());
        user.setTelephone(user.getTelephone().trim());
        CommonResult<User> result = new CommonResult<>();
        LambdaQueryWrapper<User> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(User::getUsername, user.getUsername());
        User exist1 = userMapper.selectOne(wrapper1);

        LambdaQueryWrapper<User> wrapper2 = new LambdaQueryWrapper<>();
        wrapper2.eq(User::getTelephone, user.getTelephone());
        User exist2 = userMapper.selectOne(wrapper2);

        if (exist1 != null) {
            result.usernameExist(); //104
        } else if (exist2 != null) {
            result.phoneExist(); //105
        } else {
            user.setId(null);
            user.setPassword(encodePassword(user.getPassword()));
            if (user.getIdNum() != null && !user.getIdNum().isBlank()) {
                LocalDate birthday = birthdayFromIdNumber(user.getIdNum().trim());
                user.setBirthday(birthday);
                user.setAge(Period.between(birthday, LocalDate.now()).getYears());
            } else if (user.getBirthday() != null && !user.getBirthday().isAfter(LocalDate.now())) {
                user.setAge(Period.between(user.getBirthday(), LocalDate.now()).getYears());
            }
            if (user.getPoint() == null) {
                user.setPoint(0);
            }
            int i = userMapper.insert(user);
            if (i <= 0) {
                result.setNotInserted(); //101
            } else {
                result.setOK(user);
            }
        }
        return result;
    }

    @Override
    public CommonResult<LocalDate> update(User user) {
        if (user == null || user.getId() == null) {
            return CommonResult.validateFailed("用户编号不能为空");
        }
        if (userMapper.selectById(user.getId()) == null) {
            return CommonResult.notFound("用户不存在");
        }
        String validationError = validateUpdate(user);
        if (validationError != null) {
            return CommonResult.validateFailed(validationError);
        }
        if (user.getUsername() != null) {
            user.setUsername(user.getUsername().trim());
            LambdaQueryWrapper<User> usernameQuery = new LambdaQueryWrapper<>();
            usernameQuery.eq(User::getUsername, user.getUsername()).ne(User::getId, user.getId());
            if (userMapper.selectCount(usernameQuery) > 0) {
                return CommonResult.failed(ResultCode.usernameExist);
            }
        }
        if (user.getTelephone() != null) {
            user.setTelephone(user.getTelephone().trim());
            LambdaQueryWrapper<User> phoneQuery = new LambdaQueryWrapper<>();
            phoneQuery.eq(User::getTelephone, user.getTelephone()).ne(User::getId, user.getId());
            if (userMapper.selectCount(phoneQuery) > 0) {
                return CommonResult.failed(ResultCode.telephoneExist);
            }
        }
        if (user.getName() != null) {
            user.setName(user.getName().trim());
        }
        if (user.getIdNum() != null && !user.getIdNum().isBlank()) {
            user.setIdNum(user.getIdNum().trim());
            LambdaQueryWrapper<User> idNumberQuery = new LambdaQueryWrapper<>();
            idNumberQuery.eq(User::getIdNum, user.getIdNum()).ne(User::getId, user.getId());
            if (userMapper.selectCount(idNumberQuery) > 0) {
                return CommonResult.validateFailed("身份证号已被使用");
            }
            user.setBirthday(birthdayFromIdNumber(user.getIdNum()));
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(null);
        } else if (!isBcryptHash(user.getPassword())) {
            user.setPassword(encodePassword(user.getPassword()));
        }
        if (user.getBirthday() != null && !user.getBirthday().isAfter(LocalDate.now())) {
            user.setAge(Period.between(user.getBirthday(), LocalDate.now()).getYears());
        }
        int i = userMapper.updateById(user);
        CommonResult<LocalDate> result = new CommonResult<>(user.getBirthday());
        if (i <= 0) {
            result.setNotUpdate();
        }
        return result;
    }

    @Override
    public CommonResult<List<User>> selectByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        List<User> list=baseMapper.selectList(queryWrapper);
        CommonResult<List<User>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<User>> selectByRid(int rid) {
        List<User> list=userMapper.selectByRid(rid);
        CommonResult<List<User>> result=new CommonResult<>(list);
        return result;
    }

    @Override
    public CommonResult<List<User>> selectByRidByPage(int rid, int current, int size) {
        current = Math.max(current, 1);
        size = Math.max(1, Math.min(size, 100));
        int start=(current-1)*size;
        List<User> list=userMapper.selectByRidByPage(rid,start,size);
        List<User> getTotal=userMapper.selectByRid(rid);
        int total=getTotal.size();
        CommonResult<List<User>> result=new CommonResult<>(String.valueOf(total),list);
        return result;
    }

    @Override
    public CommonResult<User> selectById(int id) {
        User user=userMapper.selectById(id);
        CommonResult<User> result=new CommonResult<>(user);
        return result;
    }

    @Override
    public CommonResult<List<User>> selectByNameOrIdNum(String searchName) {
        String idNumFormat="^[1-9]\\d{5}(18|19|20)\\d{2}((0[1-9]|1[0-2]))(([0-2][1-9]|10|20|30|31))\\d{3}[0-9Xx]$";
        //身份证号查询
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (searchName.matches(idNumFormat)) {
            wrapper.eq(User::getIdNum, searchName);
            List<User> user = userMapper.selectList(wrapper);
            return new CommonResult<>(user);
        }
        //姓名查找
        else {
            wrapper.eq(User::getName, searchName);
            List<User> user = userMapper.selectList(wrapper);
            return new CommonResult<>(user);
        }
    }

    @Override
    public CommonResult<Integer> getSum(int rId) {
        List<User> list=userMapper.selectByRid(rId);
        CommonResult<Integer> result=new CommonResult<>(list.size());
        return result;
    }

    @Override
    public CommonResult<Integer> getUsersSum() {
        List<User> list=userMapper.selectByRid(4);
        CommonResult<Integer> result=new CommonResult<>(list.size());
        return result;
    }

    @Override
    public CommonResult<Integer> getDoctorSum() {
        List<User> list=userMapper.selectByRid(3);
        CommonResult<Integer> result=new CommonResult<>(list.size());
        return result;
    }

    @Override
    public CommonResult<Integer> getWorkerSum() {
        List<User> list=userMapper.selectByRid(2);
        CommonResult<Integer> result=new CommonResult<>(list.size());
        return result;
    }

    private String encodePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return passwordEncoder.encode(rawPassword);
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBcryptHash(String password) {
        return password != null && password.matches("^\\$2[aby]\\$\\d{2}\\$.*");
    }

    private String validateRegistration(User user) {
        if (user == null) {
            return "注册信息不能为空";
        }
        if (user.getUsername() == null || !user.getUsername().matches("[A-Za-z0-9_]{5,32}")) {
            return "用户名需5-32位，且只能包含字母、数字和下划线";
        }
        if (user.getPassword() == null || user.getPassword().length() < 8 || user.getPassword().length() > 72) {
            return "密码需8-72位";
        }
        if (user.getTelephone() == null || !user.getTelephone().matches("1[3-9]\\d{9}")) {
            return "手机号格式不正确";
        }
        if (user.getName() == null || user.getName().isBlank() || user.getName().trim().length() > 50) {
            return "姓名不能为空且不能超过50字";
        }
        if (user.getIdNum() == null
                || !user.getIdNum().matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$")) {
            return "身份证号格式不正确";
        }
        try {
            LocalDate birthday = LocalDate.parse(user.getIdNum().substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
            if (birthday.isAfter(LocalDate.now())) {
                return "出生日期不能晚于今天";
            }
        } catch (RuntimeException ex) {
            return "身份证号中的出生日期无效";
        }
        return null;
    }

    private String validateManagedUser(User user) {
        if (user == null) {
            return "用户信息不能为空";
        }
        if (user.getRoleId() == null || user.getRoleId() < 2 || user.getRoleId() > 4) {
            return "只能创建工作人员、医护人员或普通用户";
        }
        if (user.getUsername() == null || !user.getUsername().trim().matches("[A-Za-z0-9_]{5,32}")) {
            return "用户名需5-32位，且只能包含字母、数字和下划线";
        }
        if (user.getPassword() == null || user.getPassword().length() < 8 || user.getPassword().length() > 72) {
            return "密码需8-72位";
        }
        if (user.getName() == null || user.getName().isBlank() || user.getName().trim().length() > 50) {
            return "姓名不能为空且不能超过50字";
        }
        if (user.getTelephone() == null || !user.getTelephone().trim().matches("1[3-9]\\d{9}")) {
            return "手机号格式不正确";
        }
        if (user.getIdNum() != null && !user.getIdNum().isBlank()) {
            try {
                birthdayFromIdNumber(user.getIdNum().trim());
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
        }
        return validateOptionalProfileFields(user);
    }

    private String validateUpdate(User user) {
        if (user.getRoleId() != null && (user.getRoleId() < 0 || user.getRoleId() > 4)) {
            return "用户角色无效";
        }
        if (user.getUsername() != null
                && !user.getUsername().trim().matches("[A-Za-z0-9_]{5,32}")) {
            return "用户名需5-32位，且只能包含字母、数字和下划线";
        }
        if (user.getPassword() != null && !user.getPassword().isBlank()
                && !isBcryptHash(user.getPassword())
                && (user.getPassword().length() < 8 || user.getPassword().length() > 72)) {
            return "密码需8-72位";
        }
        if (user.getName() != null && (user.getName().isBlank() || user.getName().trim().length() > 50)) {
            return "姓名不能为空且不能超过50字";
        }
        if (user.getTelephone() != null && !user.getTelephone().trim().matches("1[3-9]\\d{9}")) {
            return "手机号格式不正确";
        }
        if (user.getIdNum() != null && !user.getIdNum().isBlank()) {
            try {
                birthdayFromIdNumber(user.getIdNum().trim());
            } catch (IllegalArgumentException ex) {
                return ex.getMessage();
            }
        }
        return validateOptionalProfileFields(user);
    }

    private String validateOptionalProfileFields(User user) {
        if (user.getSex() != null && !user.getSex().isBlank()
                && !user.getSex().matches("男|女|0|1|2")) {
            return "性别信息无效";
        }
        if (user.getBirthday() != null && user.getBirthday().isAfter(LocalDate.now())) {
            return "出生日期不能晚于今天";
        }
        if (user.getAddress() != null && user.getAddress().length() > 255) {
            return "地址不能超过255字";
        }
        if (user.getDepartment() != null && user.getDepartment().length() > 255) {
            return "部门不能超过255字";
        }
        return null;
    }

    private LocalDate birthdayFromIdNumber(String idNumber) {
        if (!idNumber.matches("^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$")) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        try {
            LocalDate birthday = LocalDate.parse(idNumber.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
            if (birthday.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("出生日期不能晚于今天");
            }
            return birthday;
        } catch (java.time.DateTimeException ex) {
            throw new IllegalArgumentException("身份证号中的出生日期无效");
        }
    }
}
