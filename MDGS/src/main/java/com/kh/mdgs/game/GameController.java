package com.kh.mdgs.game;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    // "" (홈 화면). q 파라미터가 있으면 검색, 없으면 전체 목록
    @GetMapping("/")
    public String list(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("gameList", gameService.getGameList(q));
        return "gameList";
    }

    // /gameDetail?id=N
    // id가 숫자가 아니면(또는 없으면) game=null로 넘겨서, 뷰가 "존재하지 않는 게임입니다" 안내를 표시
    @GetMapping("/gameDetail")
    public String detail(@RequestParam(value = "id", required = false) String id, Model model) {
        Game game;
        try {
            game = gameService.getGameById(Integer.parseInt(id));
        } catch (NumberFormatException e) {
            game = null;
        }
        model.addAttribute("game", game);
        return "gameDetail";
    }
}
