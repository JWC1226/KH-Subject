package com.kh.mdgs.member;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    // 생성자 주입 방식 활용
    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public boolean register(User user) {
        return userMapper.insert(user) > 0;
    }

    // 로그인 성공 시 User_SID 반환, 실패 시 null
    public String login(String id, String password) {
        User found = userMapper.login(new User(null, id, password, null, 0, null, null));
        return (found != null) ? found.getUserSid() : null;
    }

    public boolean isDuplicateId(String id) {
        return userMapper.countById(id) > 0;
    }

    public User getUserBySid(String userSid) {
        return userMapper.findBySid(userSid);
    }

    public boolean updateUser(String userSid, String email, int age, String address, String cardNum) {
        User user = new User(userSid, null, null, email, age, address, cardNum);
        return userMapper.update(user) > 0;
    }

    public boolean deleteUser(String userSid) {
        return userMapper.delete(userSid) > 0;
    }

    public List<User> getAllUsers() {
        return userMapper.findAll();
    }
}
