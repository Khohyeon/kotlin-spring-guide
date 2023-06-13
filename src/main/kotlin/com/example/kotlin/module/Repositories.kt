package com.example.kotlin.module

import org.springframework.data.jpa.repository.JpaRepository

interface ArticleRepository : JpaRepository<Article, Long> {
    fun findBySlug(slug: String): Article?
    fun findAllByOrderByAddedAtDesc(): List<Article>
}

interface UserRepository : JpaRepository<User, Long> {
    fun findByLogin(login: String): User?
}