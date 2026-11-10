package com.kh.mdgs.game;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

@Mapper // MyBatis Mapper 인터페이스임을 명시 (Spring Bean으로 등록)
public interface GameMapper {
    List<Game> findAll();
    List<Game> search(String keyword);
    Game findById(int gameId);
}
