package com.kh.mdgs.game;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class GameService {

    private final GameMapper gameMapper;

    // 생성자 주입 방식 활용
    public GameService(GameMapper gameMapper) {
        this.gameMapper = gameMapper;
    }

    // 검색어(q)가 있으면 검색, 없으면 전체 목록
    public List<Game> getGameList(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            return gameMapper.search(keyword.trim());
        }
        return gameMapper.findAll();
    }

    public Game getGameById(int gameId) {
        return gameMapper.findById(gameId);
    }
}
