package com.kh.mdgs.member;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

@Mapper // MyBatis Mapper 인터페이스임을 명시 (Spring Bean으로 등록)
public interface UserMapper {
    int insert(User user);
    User login(User user);
    int countById(String id);
    User findBySid(String userSid);
    int update(User user);
    int delete(String userSid);
    List<User> findAll();
}
