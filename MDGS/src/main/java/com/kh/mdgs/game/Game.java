package com.kh.mdgs.game;

import java.sql.Date;

// Lombok 없이 직접 작성한 순수 POJO (생성자 + getter/setter)
// setter는 MyBatis가 SELECT 결과를 이 객체에 자동으로 채워 넣을 때 필요합니다
public class Game {
    private int gameId;
    private String title;
    private String genre;
    private double price;
    private Date releaseDate;
    private String developer;
    private String imagePath;

    public Game() {
    }

    public Game(int gameId, String title, String genre, double price, Date releaseDate, String developer, String imagePath) {
        this.gameId = gameId;
        this.title = title;
        this.genre = genre;
        this.price = price;
        this.releaseDate = releaseDate;
        this.developer = developer;
        this.imagePath = imagePath;
    }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public Date getReleaseDate() { return releaseDate; }
    public void setReleaseDate(Date releaseDate) { this.releaseDate = releaseDate; }

    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    @Override
    public String toString() {
        return String.format("[%d] %s | 장르: %s | 가격: $%.2f | 개발사: %s", gameId, title, genre, price, developer);
    }
}
