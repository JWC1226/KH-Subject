package com.kh.mdgs.member;

// Lombok 없이 직접 작성한 순수 POJO (생성자 + getter/setter)
// setter는 MyBatis가 SELECT 결과를 이 객체에 자동으로 채워 넣을 때 필요합니다
public class User {
    private String userSid;
    private String id;
    private String password;
    private String email;
    private int age;
    private String address;
    private String cardNum;

    public User() {
    }

    public User(String userSid, String id, String password, String email, int age, String address, String cardNum) {
        this.userSid = userSid;
        this.id = id;
        this.password = password;
        this.email = email;
        this.age = age;
        this.address = address;
        this.cardNum = cardNum;
    }

    public String getUserSid() { return userSid; }
    public void setUserSid(String userSid) { this.userSid = userSid; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }

    @Override
    public String toString() {
        return String.format("User[userSid=%s, id=%s, email=%s, age=%d, address=%s, cardNum=%s]",
                userSid, id, email, age, address, cardNum);
    }
}
