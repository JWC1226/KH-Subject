-- ==========================================
-- 1. 계정 생성
--    (SYS 계정 sys as sysdba, 설치 시 정한 비밀번호로 접속한 상태에서 실행)
-- 2. 기본 권한 및 객체 생성 권한 부여
-- 3. 테이블스페이스(데이터 저장 공간) 할당량 부여
-- ==========================================
CREATE USER C##MDGS IDENTIFIED BY mdgs;
GRANT CONNECT, RESOURCE, DBA TO C##MDGS;
ALTER USER C##MDGS QUOTA UNLIMITED ON USERS;

-- ==========================================
-- ↓ 여기부터는 C##MDGS / mdgs 계정으로 새로 접속해서 실행
-- ==========================================

-- ==========================================
-- 4. 기존 객체 삭제 (재실행 시에만 필요 — 처음 실행할 땐 주석 상태 그대로 두면 됨)
-- ==========================================
-- DROP TABLE GAMES CASCADE CONSTRAINTS;
-- DROP TABLE USERS CASCADE CONSTRAINTS;

-- ==========================================
-- 5. USERS (회원) 테이블 생성
-- ==========================================
CREATE TABLE USERS (
    User_SID   VARCHAR2(30)  NOT NULL,
    ID         VARCHAR2(50)  NOT NULL,
    Password   VARCHAR2(100) NOT NULL,
    Email      VARCHAR2(100),
    Age        NUMBER(3),
    Address    VARCHAR2(200),
    Card_Num   VARCHAR2(30),

    CONSTRAINT PK_USERS    PRIMARY KEY (User_SID),
    CONSTRAINT UQ_USERS_ID UNIQUE (ID)
);

-- 관리자 계정: 일반 회원가입을 거치지 않고 DB에 직접 만드는 계정.
-- ID 값 'MDGS1226' 자체가 곧 "관리자 여부" 판단 기준이므로(MemberController/SupportController의 ADMIN_ID 상수),
-- 여기 값을 바꾸면 그 두 곳의 상수도 같이 바꿔야 한다.
INSERT INTO USERS (User_SID, ID, Password, Email, Age, Address, Card_Num)
VALUES ('U_ADMIN', 'MDGS1226', 'admin1226', 'admin@mdgs.com', 30, '서울', NULL);
COMMIT;


-- ==========================================
-- 6. GAMES (게임 목록) 테이블 생성
-- ==========================================
CREATE TABLE GAMES (
    GAME_ID       NUMBER        NOT NULL,
    TITLE         VARCHAR2(200) NOT NULL,
    GENRE         VARCHAR2(50),
    PRICE         NUMBER(10,2),
    RELEASE_DATE  DATE,
    DEVELOPER     VARCHAR2(100),
    IMAGE_PATH    VARCHAR2(200),

    CONSTRAINT PK_GAMES PRIMARY KEY (GAME_ID)
);

INSERT INTO GAMES (GAME_ID, TITLE, GENRE, PRICE, RELEASE_DATE, DEVELOPER, IMAGE_PATH)
VALUES (1, 'Hades', '액션 로그라이크', 24.99, DATE '2020-09-17', 'Supergiant Games', 'resources/images/HADES_4k.jpg');
INSERT INTO GAMES (GAME_ID, TITLE, GENRE, PRICE, RELEASE_DATE, DEVELOPER, IMAGE_PATH)
VALUES (2, 'Rainbow Six Siege', 'FPS', 19.99, DATE '2015-12-01', 'Ubisoft', 'resources/images/R6_4k.jpg');
INSERT INTO GAMES (GAME_ID, TITLE, GENRE, PRICE, RELEASE_DATE, DEVELOPER, IMAGE_PATH)
VALUES (3, 'Clair Obscur: Expedition 33', '풍부한 스토리', 49.99, DATE '2025-04-24', 'Sandfall Interactive', 'resources/images/Clair_Obscur_Expedition_33_4k.jpg');
INSERT INTO GAMES (GAME_ID, TITLE, GENRE, PRICE, RELEASE_DATE, DEVELOPER, IMAGE_PATH)
VALUES (4, 'Diablo IV', '핵 앤 슬래시', 39.99, DATE '2023-06-06', 'Blizzard Entertainment', 'resources/images/DIAVLO_IV_4k.png');
COMMIT;


-- ==========================================
-- 7. 테이블/컬럼 설명 코멘트
-- ==========================================
COMMENT ON TABLE  USERS             IS 'MDGS 회원 정보 테이블';
COMMENT ON COLUMN USERS.User_SID    IS '회원 고유 식별 번호 (PK, 가입 시각 기반 문자열)';
COMMENT ON COLUMN USERS.ID          IS '로그인 아이디 (UNIQUE)';
COMMENT ON COLUMN USERS.Password    IS '로그인 비밀번호 (평문 저장 — 학습용 단순화)';

COMMENT ON TABLE  GAMES             IS 'MDGS 게임 목록 테이블';
COMMENT ON COLUMN GAMES.GAME_ID     IS '게임 고유 식별 번호 (PK)';
COMMENT ON COLUMN GAMES.TITLE       IS '게임 제목 (검색 대상 컬럼)';
COMMENT ON COLUMN GAMES.IMAGE_PATH  IS 'resources/images/... 형태의 상대 경로';
