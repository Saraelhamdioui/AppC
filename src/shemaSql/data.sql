-- ============================
-- Base de données ChatApp
-- ============================

CREATE DATABASE IF NOT EXISTS chat_app;
USE chat_app;

CREATE TABLE IF NOT EXISTS users (
                                     id       INT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(50) UNIQUE NOT NULL
    );

CREATE TABLE IF NOT EXISTS messages (
                                        id         INT AUTO_INCREMENT PRIMARY KEY,
                                        sender     VARCHAR(50)  NOT NULL,
    receiver   VARCHAR(50)  NOT NULL,
    content    TEXT         NOT NULL,
    seen       BOOLEAN      DEFAULT FALSE,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS calls (
                                     id         INT AUTO_INCREMENT PRIMARY KEY,
                                     caller     VARCHAR(50)  NOT NULL,
    callee     VARCHAR(50)  NOT NULL,
    type       ENUM('audio','video') NOT NULL,
    status     ENUM('ongoing','ended') DEFAULT 'ongoing',
    start_time DATETIME,
    end_time   DATETIME,
    duration   INT DEFAULT 0  -- en secondes
    );
